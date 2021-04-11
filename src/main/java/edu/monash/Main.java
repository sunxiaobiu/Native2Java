package edu.monash;

import edu.monash.model.NativeInvocation;
import edu.monash.transfer.Native2Java;
import edu.monash.utils.FileCollector;
import org.apache.commons.collections4.CollectionUtils;
import soot.G;
import soot.PackManager;
import soot.Scene;
import soot.Transform;
import soot.options.Options;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) throws IOException {
        String apkPath = args[0];
        String path2AndroidJar = args[1];
        String osReaultsPath = args[2];

        String outputPath = "instrumented_app";
        if(args.length == 4){
            outputPath = args[3];
        }

        //Read data from **.so.result files, stored in GlobalRef.nativeInvocationList
        collectSoResultsData(osReaultsPath);

        //The instrument process will be executed only if valid os.results are provided.
        if (!needInstrument()) return;

        //Instrument APK by transforming native API to its corresponding target java API.
        instrumentAPK(apkPath, path2AndroidJar, outputPath);
    }

    private static void instrumentAPK(String apkPath, String path2AndroidJar, String outputPath) {
        G.reset();

        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_output_format(Options.output_format_dex);
        Options.v().set_process_multiple_dex(true);
        Options.v().set_search_dex_in_archives(true);
        Options.v().set_no_bodies_for_excluded(true);
        Options.v().set_android_jars(path2AndroidJar);
        Options.v().set_process_dir(Collections.singletonList(apkPath));
        Options.v().set_whole_program(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_keep_line_number(true);
        Options.v().set_ignore_resolution_errors(true);
        Options.v().set_output_dir(outputPath);
        Options.v().set_prepend_classpath(true);
        Options.v().setPhaseOption("cg.cha", "on");
        Options.v().set_force_overwrite(true);
        Options.v().set_full_resolver(true);

        Scene.v().loadNecessaryClasses();
        Scene.v().loadBasicClasses();
        Scene.v().loadDynamicClasses();

        PackManager.v().getPack("wjtp").add(new Transform("wjtp.Native2Java", new Native2Java()));

        PackManager.v().runPacks();
        PackManager.v().writeOutput();
    }

    private static boolean needInstrument() {
        if(CollectionUtils.isEmpty(GlobalRef.nativeInvocationList)){
            System.out.println("NativeInvocationList is NONE, no need to instrument!");
            return false;
        }

        List<NativeInvocation> effectiveInvocationList = GlobalRef.nativeInvocationList.stream().filter(nativeInvocation -> {
            return Boolean.TRUE.equals(nativeInvocation.hasInvokee);
        }).collect(Collectors.toList());
        if(CollectionUtils.isEmpty(effectiveInvocationList)){
            System.out.println("No effective invocations in this App, no need to instrument!");
            return false;
        }
        return true;
    }

    private static void collectSoResultsData(String outputPath) throws IOException {
        List<String> soResFiles = FileCollector.getFileList(outputPath);
        if(CollectionUtils.isEmpty(soResFiles)){
            throw new RuntimeException("Fail to find any so.result files in the given directory!");
        }

        soResFiles.forEach(soResFile -> {
            try {
                File filePath = new File(outputPath + "/" + soResFile);
                BufferedReader br = null;
                String line = "";
                br = new BufferedReader(new FileReader(filePath));

                while ((line = br.readLine()) != null) {
                    if(!line.startsWith("invoker_cls")){
                        NativeInvocation nativeInvocation = new NativeInvocation();
                        String[] contentList = line.split(", ");
                        List<String> invocationList = Arrays.asList(contentList);
                        int size = invocationList.size();
                        if(size==5){
                            nativeInvocation.hasInvokee = false;
                        }else if(size==9 || size==10){
                            nativeInvocation.hasInvokee = true;
                        }else{
                            System.err.println("Invalid Input : "+line);
                            continue;
                        }

                        if(!nativeInvocation.hasInvokee){
                            nativeInvocation.invokerCls = invocationList.get(0);
                            nativeInvocation.invokerMethod = invocationList.get(1);
                            nativeInvocation.invokerSignature = invocationList.get(2);
                            nativeInvocation.invokerSymbol = invocationList.get(3);
                            nativeInvocation.invokerStaticExport = Boolean.parseBoolean(invocationList.get(4));
                        }else{
                            nativeInvocation.invokerCls = invocationList.get(0);
                            nativeInvocation.invokerMethod = invocationList.get(1);
                            nativeInvocation.invokerSignature = invocationList.get(2);
                            nativeInvocation.invokerSymbol = invocationList.get(3);
                            nativeInvocation.invokerStaticExport = Boolean.parseBoolean(invocationList.get(4));
                            nativeInvocation.invokeeCls = invocationList.get(5);
                            nativeInvocation.invokeeMethod = invocationList.get(6);
                            nativeInvocation.invokeeSignature = invocationList.get(7);
                            nativeInvocation.invokeeStatic = Boolean.parseBoolean(invocationList.get(8));
                             if(size==10){
                                nativeInvocation.invokeeDesc = invocationList.get(9);
                            }
                        }
                        GlobalRef.nativeInvocationList.add(nativeInvocation);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}
