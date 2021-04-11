package edu.monash.transfer;

import edu.monash.GlobalRef;
import edu.monash.HeuristicUnknownValueInfer;
import edu.monash.model.NativeInvocation;
import edu.monash.utils.ApplicationClassFilter;
import edu.monash.utils.JNIResolve;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import soot.*;
import soot.dava.internal.javaRep.DIntConstant;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.*;
import soot.jimple.toolkits.typing.fast.Integer127Type;
import soot.jimple.toolkits.typing.fast.Integer1Type;
import soot.jimple.toolkits.typing.fast.Integer32767Type;
import soot.util.Chain;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Native2Java extends SceneTransformer {
    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {

        for(NativeInvocation nativeInvocation : GlobalRef.nativeInvocationList){
            if(!nativeInvocation.hasInvokee){
                continue;
            }

            /**
             * Preprocess - data collection
             */
            //extract Args
            List<Type> sootMethodArgs = JNIResolve.extractMethodArgs(nativeInvocation.invokerSignature);

            //extract return value
            Type returnType = JNIResolve.extractMethodReturnType(nativeInvocation.invokerSignature);

            //Pinpoint native method and its declare class
            SootClass declareCls = Scene.v().getSootClass(nativeInvocation.invokerCls);
            SootMethod nativeMethod = declareCls.getMethod(nativeInvocation.invokerMethod, sootMethodArgs);
            if(null == nativeMethod){
                System.err.println("Invalid nativeMethod method! [nativeMethodName]:"+nativeInvocation.invokerMethod);
                continue;
            }
            if(!nativeMethod.isNative()){
                nativeInvocation.substitudeMethodExist = true;
            }


            /**
             * step1. Insert a substitute method into declare class
             * public returnType nativeMethodName(ParamTypes) {
             *
             * }
             */
            JimpleBody body;
            Chain units;
            if(!nativeInvocation.substitudeMethodExist){
                SootMethod substituteMethod;
                if(nativeMethod.isStatic()){
                    substituteMethod = new SootMethod(nativeInvocation.invokerMethod, sootMethodArgs, returnType, Modifier.PUBLIC | Modifier.STATIC);
                }else{
                    substituteMethod = new SootMethod(nativeInvocation.invokerMethod, sootMethodArgs, returnType, Modifier.PUBLIC);
                }
                declareCls.removeMethod(nativeMethod);
                declareCls.addMethod(substituteMethod);

                // create empty body
                body = Jimple.v().newBody(substituteMethod);
                LocalGenerator lg = new LocalGenerator(body);

                // new
                if(!nativeMethod.isStatic()){
                    Local al = lg.generateLocal(declareCls.getType());
                    Unit newU = (Unit) Jimple.v().newIdentityStmt(al, Jimple.v().newThisRef(declareCls.getType()));
                    body.getUnits().add(newU);
                }

                // all parameters
                for(int i = 0; i<sootMethodArgs.size(); i++){
                    Type paramType = sootMethodArgs.get(i);
                    Local originParameterLocal = lg.generateLocal(paramType);
                    Unit originParameterU = Jimple.v().newIdentityStmt(originParameterLocal, Jimple.v().newParameterRef(paramType, i));
                    body.getUnits().add(originParameterU);
                }

                substituteMethod.setActiveBody(body);
                units = body.getUnits();
            }else {
                body = (JimpleBody)nativeMethod.getActiveBody();
                units = body.getUnits();
            }


            /**
             * Heuristic analysis for unknown class name inferring
             */
            HeuristicUnknownValueInfer.getInstance().infer(nativeInvocation);

            /**
             * Skip invalid invokee Method.
             */
            SootMethod invokeeMethod = null;
            try {
                List<Type> invokeeMethodArgs = JNIResolve.extractMethodArgs(nativeInvocation.invokeeSignature);
                SootClass invokeeCls = Scene.v().getSootClass(nativeInvocation.invokeeCls);
                invokeeMethod = invokeeCls.getMethod(nativeInvocation.invokeeMethod, invokeeMethodArgs);
            } catch (Exception e) {
                System.err.println("Invalid invokee Method! [invokeeCls]:"+nativeInvocation.invokeeCls +"; [invokeeMethod]:"+nativeInvocation.invokeeMethod+"; [invokeeMethodArgs]:"+nativeInvocation.invokeeSignature);
                continue;
            }
            if(null == invokeeMethod){
                System.err.println("Invalid invokee Method! [invokeeCls]:"+nativeInvocation.invokeeCls +"; [invokeeMethod]:"+nativeInvocation.invokeeMethod+"; [invokeeMethodArgs]:"+nativeInvocation.invokeeSignature);
                continue;
            }


            /**
             * step2. Insert target API into nativeMethod
             */
            if(StringUtils.isNotBlank(nativeInvocation.invokeeCls)){
                insertTargetAPI(nativeInvocation, body, units, nativeInvocation.invokeeCls);
            }else if(CollectionUtils.isNotEmpty(nativeInvocation.inferredClsList)){
                for(String inferredCls : nativeInvocation.inferredClsList){
                    insertTargetAPI(nativeInvocation, body, units, inferredCls);
                }
            }

            //add stmt "return xxx;" in units for ending
            if(units.getPredOf(units.getLast()) instanceof ReturnStmt){
                units.remove(units.getPredOf(units.getLast()));
            }
            Type targetAPIReturnType = JNIResolve.extractMethodReturnType(nativeInvocation.invokeeSignature);
            if(units.getLast() instanceof AssignStmt && ((AssignStmt) units.getLast()).getLeftOp().getType().toString().equals(targetAPIReturnType.toString())){
                units.add(Jimple.v().newReturnStmt(((AssignStmt) units.getLast()).getLeftOp()));
            }else{
                Local returnValueLocal = Jimple.v().newLocal("returnValue", targetAPIReturnType);
                body.getLocals().add(returnValueLocal);
                units.add(Jimple.v().newReturnStmt(returnValueLocal));
            }

        }
    }

    private void insertTargetAPI(NativeInvocation nativeInvocation, JimpleBody body, Chain units, String invokeeCls) {
        List<Type> targetAPIParamTypes = JNIResolve.extractMethodArgs(nativeInvocation.invokeeSignature);
        Type targetAPIReturnType = JNIResolve.extractMethodReturnType(nativeInvocation.invokeeSignature);
        SootClass targetAPICls = Scene.v().getSootClass(invokeeCls);

        // add returnValue local
        Local returnValueLocal = Jimple.v().newLocal("returnValue", targetAPIReturnType);
        body.getLocals().add(returnValueLocal);

        if(nativeInvocation.invokeeStatic){
            if(targetAPIParamTypes.size() == 0){
                units.add(Jimple.v().newAssignStmt(returnValueLocal, Jimple.v().newStaticInvokeExpr(Scene.v().makeMethodRef(targetAPICls, nativeInvocation.invokeeMethod, targetAPIParamTypes, targetAPIReturnType, true))));
            }else{
                List<Value> values = generateParamValues(body, targetAPIParamTypes);
                units.add(Jimple.v().newAssignStmt(returnValueLocal, Jimple.v().newStaticInvokeExpr(Scene.v().makeMethodRef(targetAPICls, nativeInvocation.invokeeMethod, targetAPIParamTypes, targetAPIReturnType, true), values)));
            }
        }else{
            Local invokeeLocal = Jimple.v().newLocal("invokeeObject", RefType.v(invokeeCls));
            body.getLocals().add(invokeeLocal);

            if(targetAPIParamTypes.size() == 0){
                units.add(Jimple.v().newAssignStmt(returnValueLocal, Jimple.v().newSpecialInvokeExpr(
                        invokeeLocal, Scene.v().makeMethodRef(targetAPICls, nativeInvocation.invokeeMethod, targetAPIParamTypes, targetAPIReturnType, false))));
            }else{
                List<Value> values = generateParamValues(body, targetAPIParamTypes);

                // TODO: 17/3/21 确认下list mock value是否正确
                units.add(Jimple.v().newAssignStmt(returnValueLocal, Jimple.v().newSpecialInvokeExpr(
                        invokeeLocal, Scene.v().makeMethodRef(targetAPICls, nativeInvocation.invokeeMethod, targetAPIParamTypes, targetAPIReturnType, false), values)));
            }
        }
    }

    private List<Value> generateParamValues(JimpleBody body, List<Type> targetAPIParamTypes) {
        return targetAPIParamTypes.stream().map(paramType -> {
            //Heuristic Rule1. check if invoker Parameter matches invokee Parameter
            for(Local paramLocal : body.getParameterLocals()){
                if (paramType.toString().equals(paramLocal.getType().toString())) {
                    return paramLocal;
                }
            }

            //Heuristic Rule2. check if return value of pre-units match invokee Parameter
            if(CollectionUtils.isNotEmpty(body.getUnits())){
                for (Unit preUnit : body.getUnits()) {
                    if (preUnit instanceof AssignStmt && ((AssignStmt) preUnit).getLeftOp().getType().toString().equals(paramType.toString())) {
                        return ((AssignStmt) preUnit).getLeftOp();
                    }
                }
            }

            //Heuristic Rule3. generate dummy value
            return mockValueFromType(paramType);
        }).collect(Collectors.toList());
    }

    private Value mockValueFromType(Type paramType) {
        if(paramType instanceof PrimType){
            Value defaultValue4PrimType = newPrimType((PrimType) paramType);
            if(null != defaultValue4PrimType){
                return defaultValue4PrimType;
            }
        }

        if(ApplicationClassFilter.isJavaBasicType(paramType.toString())){
            Value defaultValue4PrimType = newPrimType(paramType.toString());
            if(null != defaultValue4PrimType){
                return defaultValue4PrimType;
            }
        }

        Local paramObject = Jimple.v().newLocal("paramObject", paramType);
        return paramObject;
    }

    private static Value newPrimType(PrimType primType) {
        if (primType instanceof BooleanType) {
            return DIntConstant.v(1, BooleanType.v());
        } else if (primType instanceof ByteType) {
            return DIntConstant.v(1, ByteType.v());
        } else if (primType instanceof CharType) {
            return DIntConstant.v(32, CharType.v());
        } else if (primType instanceof DoubleType) {
            return DoubleConstant.v(1.0);
        } else if (primType instanceof FloatType) {
            return FloatConstant.v((float) 1.0);
        } else if (primType instanceof IntType) {
            return IntConstant.v(1);
        } else if (primType instanceof Integer127Type) {
            return IntConstant.v(1);
        } else if (primType instanceof Integer1Type) {
            return IntConstant.v(1);
        } else if (primType instanceof Integer32767Type) {
            return IntConstant.v(1);
        } else if (primType instanceof LongType) {
            return LongConstant.v(1);
        } else if (primType instanceof ShortType) {
            return IntConstant.v(1);
        }
        return null;
    }

    private static Value newPrimType(String className) {
        if (className.startsWith("java.lang.String")) {
            return StringConstant.v("mock_value");
        } else if (className.startsWith("java.lang.Boolean")) {
            return  DIntConstant.v(1, BooleanType.v());
        } else if (className.startsWith("java.lang.Byte")) {
            return DIntConstant.v(1, ByteType.v());
        } else if (className.startsWith("java.lang.Character")) {
            return DIntConstant.v(1, CharType.v());
        } else if (className.startsWith("java.lang.Double")) {
            return DoubleConstant.v(1.0);
        } else if (className.startsWith("java.lang.Float")) {
            return FloatConstant.v((float) 1.0);
        } else if (className.startsWith("java.lang.Integer")) {
            return IntConstant.v(1);
        } else if (className.startsWith("java.lang.Long")) {
            return LongConstant.v(1);
        } else if (className.startsWith("java.lang.Short")) {
            return IntConstant.v(1);
        }
        return null;
    }
}
