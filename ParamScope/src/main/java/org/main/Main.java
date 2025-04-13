package org.main;

import org.apache.commons.cli.*;
import org.paramscope.analysis.AnalysisEnv;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {

        Options options = new Options();

        options.addOption("apk", true, "Specify APK file");
        options.addOption("jar", true, "Specify JAR file");
        options.addOption("androidJar", "aj", true, "Specify path to android.jar");

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);

            if (cmd.hasOption("apk") && cmd.hasOption("jar")) {
                throw new ParseException("Only one of -apk or -jar option must be specified");
            }
            if (!cmd.hasOption("apk") && !cmd.hasOption("jar")) {
                throw new ParseException("Either -apk or -jar option must be specified");
            }

            String fileType = cmd.hasOption("apk") ? "apk" : "jar";
            String filePath = cmd.getOptionValue(fileType);

            if ("apk".equalsIgnoreCase(fileType) && !cmd.hasOption("androidJar")) {
                throw new ParseException("The -androidJar option must be specified when using -apk");
            }

            String androidJarPath = cmd.getOptionValue("androidJar");
            Path androidJarAbsolutePath = null;
            if (androidJarPath != null) {
                androidJarAbsolutePath = Paths.get(androidJarPath).toAbsolutePath();
                if (!Files.exists(androidJarAbsolutePath)) {
                    throw new ParseException("The specified androidJar path does not exist: " + androidJarAbsolutePath);
                }
            }

            System.out.println("File Type: " + fileType);
            System.out.println("File Path: " + filePath);
            if (androidJarAbsolutePath != null) {
                System.out.println("Android JAR Path: " + androidJarAbsolutePath);
            }

            if ("apk".equalsIgnoreCase(fileType)) {
                AnalysisEnv.setAndroidJarPath(androidJarAbsolutePath.toString());
                AnalysisEnv.analysisApk(filePath);
            } else if ("jar".equalsIgnoreCase(fileType)) {
                AnalysisEnv.analyseJar(filePath);
            } else {
                System.out.println("Invalid file type. Please specify '-apk' or '-jar'.");
            }

        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("java -jar ParamScope.jar <-apk|-jar> <path to apk/jar> <-androidJar/-aj> <Path to androidJar(without stub)>", options);
            System.exit(1);
        }
    }
}