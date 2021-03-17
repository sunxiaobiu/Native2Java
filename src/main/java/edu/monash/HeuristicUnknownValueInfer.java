package edu.monash;

import edu.monash.model.NativeInvocation;
import edu.monash.utils.JNIDescriptor;
import edu.monash.utils.JNIResolve;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.coffi.Util;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HeuristicUnknownValueInfer {

    private static HeuristicUnknownValueInfer instance;

    public static HeuristicUnknownValueInfer getInstance() {
        if (null == instance) {
            instance = new HeuristicUnknownValueInfer();
        }
        return instance;
    }

    public NativeInvocation infer(NativeInvocation nativeInvocation) {
        for (Iterator<SootClass> iter = Scene.v().getClasses().snapshotIterator(); iter.hasNext(); ) {
            SootClass sc = iter.next();

            if(sc.getMethods().size() <= 0){
                continue;
            }

            for(SootMethod sm : sc.getMethods()){
                if(!nativeInvocation.invokeeMethod.equals(sm.getName())){
                    continue;
                }

                if(!nativeInvocation.invokeeStatic == sm.isStatic()){
                    continue;
                }

                Type sootMethodReturnType = JNIResolve.extractMethodReturnType(nativeInvocation.invokerSignature);
                if(!sootMethodReturnType.equals(sm.getReturnType())){
                    continue;
                }

                List<Type> sootMethodArgs = JNIResolve.extractMethodArgs(nativeInvocation.invokerSignature);
                List<Type> smParams = sm.getParameterTypes();
                List<Type> unavailable = sootMethodArgs.stream().filter(e -> (smParams.stream().filter(d -> d.equals(e)).count()) < 1)
                        .collect(Collectors.toList());
                if (unavailable.size() != 0) {
                    continue;
                }

                nativeInvocation.inferredClsList.add(sm.getDeclaringClass().getName());
            }
        }

        return nativeInvocation;
    }
}
