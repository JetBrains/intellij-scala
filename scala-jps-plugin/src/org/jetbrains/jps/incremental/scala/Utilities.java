package org.jetbrains.jps.incremental.scala;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.*;
import java.util.*;

/**
 * @author Pavel Fatin
 */
public class Utilities {
  private Utilities() {
  }

  @Nullable
  public static <A, B> Pair<A, B> head(Map<A, B> map) {
    Iterator<Map.Entry<A, B>> it = map.entrySet().iterator();

    if (!it.hasNext()) return null;

    Map.Entry<A, B> entry = it.next();
    return Pair.create(entry.getKey(), entry.getValue());
  }

  public static List<String> toNames(Collection<JpsModule> modules) {
    List<String> result = new ArrayList<String>();
    for (JpsModule module : modules) {
      result.add(module.getName());
    }
    return result;
  }

  public static List<String> toCanonicalPaths(Collection<File> files) {
    List<String> result = new ArrayList<String>();
    for (File file : files) {
      result.add(FileUtil.toCanonicalPath(file.getPath()));
    }
    return result;
  }

  public static List<String> toSystemIndependentNames(Collection<File> files) {
    List<String> result = new ArrayList<String>();
    for (File file : files) {
      result.add(FileUtil.toSystemIndependentName(file.getPath()));
    }
    return result;
  }

  public static void writeStringTo(File file, String content) throws IOException {
    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));

    try {
      writer.write(content);
      writer.flush();
    } finally {
      writer.close();
    }
  }
}
