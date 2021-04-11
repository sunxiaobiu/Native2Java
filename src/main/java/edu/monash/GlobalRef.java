package edu.monash;

import edu.monash.model.NativeInvocation;

import java.util.ArrayList;
import java.util.List;

public class GlobalRef {
    public static String appName = null;
    public static List<NativeInvocation> nativeInvocationList = new ArrayList<>();
    public static Integer minSDKVersion = 1;

    public static void print(String msg) {
        StringBuilder sb = new StringBuilder();
        sb.append(appName);
        sb.append(": ");
        sb.append(msg);
        System.out.println(sb.toString());
    }

    public static void printErr(String msg) {
        StringBuilder sb = new StringBuilder();
        sb.append(appName);
        sb.append(": ");
        sb.append(msg);
        System.err.println(sb.toString());
    }
}
