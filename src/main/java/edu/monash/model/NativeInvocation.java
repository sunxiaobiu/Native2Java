package edu.monash.model;

import java.util.ArrayList;
import java.util.List;

public class NativeInvocation {

    public String invokerCls;
    public String invokerMethod;
    public String invokerSignature;
    public String invokerSymbol;
    public String invokerStatic;
    @Deprecated
    public boolean invokerStaticExport;

    public boolean hasInvokee = false;
    public boolean substitudeMethodExist = false;

    public String invokeeCls;
    public String invokeeMethod;
    public String invokeeSignature;
    public boolean invokeeStatic;
    public String invokeeDesc;

    public List<String> inferredClsList = new ArrayList<>();
}
