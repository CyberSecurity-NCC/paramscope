package org.paramscope.set.util;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.engine.GraphvizException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public final class DotUtil {
    private DotUtil() {}

    public static void writeDotAndMaybeRenderPng(String dot, String filePathWithoutExt) {
        File dotFile = new File(filePathWithoutExt + ".dot");
        File pngFile = new File(filePathWithoutExt + ".png");
        File parentDir = dotFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        try (FileWriter writer = new FileWriter(dotFile)) {
            writer.write(dot);
        } catch (IOException e) {
            throw new RuntimeException("Failed writing dot: " + dotFile, e);
        }

        try {
            if (dot.length() <= 500_000) {
                Graphviz.fromFile(dotFile).render(Format.PNG).toFile(pngFile);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed rendering png: " + pngFile, e);
        } catch (GraphvizException e) {
            // keep dot file; skip png
        } catch (OutOfMemoryError e) {
            // skip png
        }
    }
}

