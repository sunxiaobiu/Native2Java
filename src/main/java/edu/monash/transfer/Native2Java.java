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
                throw new RuntimeException("Invalid nativeMethod method! [nativeMethodName]:"+nativeInvocation.invokerMethod);
            }

            /**
             * step1. Insert a substitute method into declare class
             * public returnType nativeMethodName(ParamTypes) {
             *
             * }
             */
            SootMethod substituteMethod;
            if(nativeMethod.isStatic()){
                substituteMethod = new SootMethod(nativeInvocation.invokerMethod, sootMethodArgs, returnType, Modifier.PUBLIC | Modifier.STATIC);
            }else{
                substituteMethod = new SootMethod(nativeInvocation.invokerMethod, sootMethodArgs, returnType, Modifier.PUBLIC);
            }
            declareCls.removeMethod(nativeMethod);
            declareCls.addMethod(substituteMethod);

            // create empty body
            JimpleBody body = Jimple.v().newBody(substituteMethod);

            substituteMethod.setActiveBody(body);
            Chain units = body.getUnits();

            /**
             * Heuristic analysis for unknown class name inferring
             */
            HeuristicUnknownValueInfer.getInstance().infer(nativeInvocation);

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
        }
    }

    private void insertTargetAPI(NativeInvocation nativeInvocation, JimpleBody body, Chain units, String invokeeCls) {
        List<Type> targetAPIParamTypes = JNIResolve.extractMethodArgs(nativeInvocation.invokeeSignature);
        Type targetAPIReturnType = JNIResolve.extractMethodReturnType(nativeInvocation.invokeeSignature);
        SootClass targetAPICls = Scene.v().getSootClass(invokeeCls);

        if(nativeInvocation.invokeeStatic){
            Local returnValueLocal = Jimple.v().newLocal("returnValue", targetAPIReturnType);
            body.getLocals().add(returnValueLocal);

            if(targetAPIParamTypes.size() == 0){
                units.add(Jimple.v().newAssignStmt(returnValueLocal, Jimple.v().newStaticInvokeExpr(Scene.v().makeMethodRef(targetAPICls, nativeInvocation.invokeeMethod, targetAPIParamTypes, targetAPIReturnType, true))));
            }else{
                List<Value> values = targetAPIParamTypes.stream().map(paramType->{
                    return mockValueFromType(paramType);
                }).collect(Collectors.toList());

                units.add(Jimple.v().newAssignStmt(returnValueLocal, Jimple.v().newStaticInvokeExpr(Scene.v().makeMethodRef(targetAPICls, nativeInvocation.invokeeMethod, targetAPIParamTypes, targetAPIReturnType, true), values)));
            }
            units.add(Jimple.v().newReturnVoidStmt());
        }else{
            Local invokeeLocal = Jimple.v().newLocal("invokeeObject", RefType.v(invokeeCls));
            body.getLocals().add(invokeeLocal);

            Local returnValueLocal = Jimple.v().newLocal("returnValue", targetAPIReturnType);
            body.getLocals().add(returnValueLocal);

            if(targetAPIParamTypes.size() == 0){
                units.add(Jimple.v().newAssignStmt(returnValueLocal, Jimple.v().newSpecialInvokeExpr(
                        invokeeLocal, Scene.v().makeMethodRef(targetAPICls, nativeInvocation.invokeeMethod, targetAPIParamTypes, targetAPIReturnType, false))));
            }else{
                // TODO: 17/3/21 确认下list mock value是否正确
                List<Value> values = targetAPIParamTypes.stream().map(paramType->{
                    return mockValueFromType(paramType);
                }).collect(Collectors.toList());

                units.add(Jimple.v().newAssignStmt(returnValueLocal, Jimple.v().newSpecialInvokeExpr(
                        invokeeLocal, Scene.v().makeMethodRef(targetAPICls, nativeInvocation.invokeeMethod, targetAPIParamTypes, targetAPIReturnType, false), values)));
            }

            units.add(Jimple.v().newReturnStmt(returnValueLocal));
        }
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
            return StringConstant.v("android");
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
