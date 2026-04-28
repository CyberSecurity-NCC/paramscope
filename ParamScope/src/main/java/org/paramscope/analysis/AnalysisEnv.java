package org.paramscope.analysis;

import org.paramscope.analysis.slice.Slicing;
import org.paramscope.data.CallRelation;
import org.paramscope.data.MainMethods;
import sootup.core.cache.provider.LRUCacheProvider;
import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.core.model.SootClass;
import sootup.core.model.SourceType;
import sootup.java.bytecode.inputlocation.ApkAnalysisInputLocation;
import sootup.java.bytecode.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.bytecode.inputlocation.JrtFileSystemAnalysisInputLocation;
import sootup.java.core.views.JavaView;

import java.io.File;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class AnalysisEnv {

    private static List<String> applicationClassNames;
    private static JavaView view;
    private static URL[] jarURLs;
    private static URLClassLoader classLoader;
    private static String fileName;
    private static String androidJarPath;

    public static void analyseJar(String jarPath) {

        Path path = Paths.get(jarPath);
        if(!path.isAbsolute()){
            path = path.toAbsolutePath();
            jarPath = path.toString();
        }
        fileName = path.getFileName().toString();

        try {
            // Use a proper file URI (Windows-safe) for URLClassLoader reflection.
            jarURLs = new URL[]{path.toUri().toURL()};
        } catch (MalformedURLException e) {
            System.out.println("AnalysisEnv.class: wrong jarURL");
            throw new RuntimeException(e);
        }

        long startTime = System.nanoTime();
        List<AnalysisInputLocation> inputLocations = new ArrayList<>();
        inputLocations.add(new JavaClassPathAnalysisInputLocation(jarPath));
        JavaView applicationView = new JavaView(inputLocations);
        setApplicationClassNames(applicationView.getClasses().stream().map(SootClass::getName).toList());
        inputLocations.add(new JrtFileSystemAnalysisInputLocation());

        setView(new JavaView(inputLocations, new LRUCacheProvider(500)));
        classLoader = new URLClassLoader(jarURLs);

        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        System.out.println("Time taken for sootUp analysis: " + duration / 1000000 + " ms.");


        startTime = System.nanoTime();
        MainMethods.setMainMethods(applicationClassNames);
        CallRelation.buildCallRelation(applicationClassNames);
        endTime = System.nanoTime();
        duration = endTime - startTime;
        System.out.println("Time taken for building methods and call relations: " + duration / 1000000 + " ms, Total classes: " + applicationClassNames.size());

        startTime = System.nanoTime();
        Slicing.runSlicing2();
        endTime = System.nanoTime();
        duration = endTime - startTime;
        System.out.println("Time taken for CryptoSolver analysis, solving and visualization: " + duration / 1000000 + " ms.");
    }

    /**
     * Initialize analysis environment for a plain jar without running any slicing/solving pipeline.
     * This is intended for standalone modules (e.g. valueflow) that reuse {@link AnalysisEnv#view()}
     * and {@link AnalysisEnv#ClassLoader()} but want full control over what analyses to execute.
     */
    public static void initJarOnly(String jarPath, boolean buildCallRelation) {

        Path path = Paths.get(jarPath);
        if (!path.isAbsolute()) {
            path = path.toAbsolutePath();
            jarPath = path.toString();
        }
        fileName = path.getFileName().toString();

        try {
            jarURLs = new URL[]{path.toUri().toURL()};
        } catch (MalformedURLException e) {
            System.out.println("AnalysisEnv.class: wrong jarURL");
            throw new RuntimeException(e);
        }

        long startTime = System.nanoTime();
        List<AnalysisInputLocation> inputLocations = new ArrayList<>();
        inputLocations.add(new JavaClassPathAnalysisInputLocation(jarPath));
        JavaView applicationView = new JavaView(inputLocations);
        setApplicationClassNames(applicationView.getClasses().stream().map(SootClass::getName).toList());
        inputLocations.add(new JrtFileSystemAnalysisInputLocation());

        setView(new JavaView(inputLocations, new LRUCacheProvider(500)));
        classLoader = new URLClassLoader(jarURLs);

        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        System.out.println("Time taken for sootUp analysis: " + duration / 1000000 + " ms.");

        if (buildCallRelation) {
            startTime = System.nanoTime();
            MainMethods.setMainMethods(applicationClassNames);
            CallRelation.buildCallRelation(applicationClassNames);
            endTime = System.nanoTime();
            duration = endTime - startTime;
            System.out.println("Time taken for building methods and call relations: " + duration / 1000000 + " ms, Total classes: " + applicationClassNames.size());
        }
    }

    public static void analysisApk(String apkPath) {

        List<AnalysisInputLocation> inputLocations = new ArrayList<>();
        URL apkURL;
        URI apkURI;
        long startTime;
        long endTime;
        long duration;

        Path path = Paths.get(apkPath);
        if(!path.isAbsolute()){
            path = path.toAbsolutePath();
            apkPath = path.toString();
        }

        System.out.println("Loading Apk...");
        System.out.println("androidJarPath: " + androidJarPath);
        try {
            apkURL = new URL("file://" + apkPath);
            apkURI = apkURL.toURI();
        } catch (MalformedURLException | URISyntaxException e) {
            System.out.println("AnalysisEnv.class: wrong jarURL");
            throw new RuntimeException(e);
        }

        try {
            startTime = System.nanoTime();
            inputLocations.add(new ApkAnalysisInputLocation(Paths.get(apkPath), SourceType.Application));

            JavaView applicationView = new JavaView(inputLocations);
            setApplicationClassNames(applicationView.getClasses().stream().map(SootClass::getName).toList());

            inputLocations.add(new JavaClassPathAnalysisInputLocation(Paths.get(androidJarPath).toAbsolutePath().toString(), SourceType.Library));
            inputLocations.add(new JrtFileSystemAnalysisInputLocation());
            setView(new JavaView(inputLocations, new LRUCacheProvider(500)));

            endTime = System.nanoTime();
            duration = endTime - startTime;
            System.out.println("Time taken for sootUp analysis: " + duration / 1000000 + " ms.");

            startTime = System.nanoTime();
            System.out.println("Building Methods...");
            MainMethods.setMainMethods(applicationClassNames);
            System.out.println("Building call relations...");
            CallRelation.buildCallRelation(applicationClassNames);

            endTime = System.nanoTime();
            duration = endTime - startTime;
            System.out.println("Time taken for building methods and call relations: " + duration / 1000000 + " ms, Total classes: " + applicationClassNames.size());

        } catch (OutOfMemoryError e) {
            System.out.println("Out of memory in sootUp analysis");
            System.exit(1);
        }

        startTime = System.nanoTime();
        ApkResourcesPack.repackResourcesToJar(apkPath);
        jarURLs = getAndroidJarURLs(apkPath);
        classLoader = new URLClassLoader(jarURLs);
        Slicing.runSlicing2();
        endTime = System.nanoTime();
        duration = endTime - startTime;
        System.out.println("Time taken for CryptoSolver analysis, solving and visualization: " + duration / 1000000 + " ms.");
    }

    private static URL[] getAndroidJarURLs(String apkPath) {
        Path path = Paths.get(apkPath);
        fileName = path.getFileName().toString();

        String dex2JarTmpPath = "tmp/" + fileName.substring(0, fileName.length() - 4) + ".jar";
        Path dex2JarPath = Paths.get(dex2JarTmpPath).toAbsolutePath();

        Path androidJarPath2 = Paths.get(androidJarPath).toAbsolutePath();

        String dex2JarPathStr = dex2JarPath.toString();
        String androidJarPathStr2 = androidJarPath2.toString();

        try {
            File dex2File = new File(dex2JarPathStr);
            File androidJarFile = androidJarPath2.toFile();
            URL dex2Url = dex2File.toURI().toURL();
            URL androidJarUrl = androidJarFile.toURI().toURL();
            return new URL[]{dex2Url, androidJarUrl};
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<String> getApplicationClassNames() {
        return applicationClassNames;
    }

    public static void setApplicationClassNames(List<String> applicationClassNames) {
        AnalysisEnv.applicationClassNames = applicationClassNames;
    }

    public static JavaView view() {
        return view;
    }

    public static void setView(JavaView view) {
        AnalysisEnv.view = view;
    }

    public static URLClassLoader ClassLoader() {
        return classLoader;
    }

    public static String getFileName() {
        return fileName;
    }

    public static String getAndroidJarPath() {
        return androidJarPath;
    }

    public static void setAndroidJarPath(String androidJarPath) {
        AnalysisEnv.androidJarPath = androidJarPath;
    }

    // Print  Jimple IR of all classes
//    private static void printAllClasses(List<String> applicationClassNames) {
//        String dir = "./IROutput_" + System.currentTimeMillis();
//        File file = new File(dir);
//        file.mkdir();
//        for(String applicationClass: applicationClassNames) {
//            JavaSootClass sootClass = view.getClass(view.getIdentifierFactory().getClassType(applicationClass)).get();
//            String IRfileName = dir + "/" + applicationClass + ".jimple";
//            File IRFile = new File(IRfileName);
//            try{
//                PrintWriter writer = new PrintWriter(IRFile);
//                JimplePrinter jimplePrinter = new JimplePrinter();
//                jimplePrinter.printTo(sootClass, writer);
//                writer.flush();
//                writer.close();
//            } catch (FileNotFoundException e) {
//                throw new RuntimeException(e);
//            }
//        }
//    }

}
