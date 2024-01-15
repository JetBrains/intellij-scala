package org.jetbrains.jps.incremental.scala.model.impl;

import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.scala.model.LibrarySettings;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.util.JpsPathUtil;
import org.jetbrains.plugins.scala.project.ScalaLibraryPropertiesStateSharedInIdeaAndJps;

import java.io.File;

public class LibrarySettingsImpl extends JpsElementBase<LibrarySettingsImpl> implements LibrarySettings {
  private final State myState;

  public LibrarySettingsImpl(State state) {
    myState = state;
  }

  @Override
  public File[] getCompilerClasspath() {
    String[] classpath = myState.getCompilerClasspath();
    return classpath == null ? new File[0] : toFiles(classpath);
  }

  @Override
  public @Nullable File getCompilerBridgeJar() {
    String url = myState.getCompilerBridgeBinaryJar();
    return url == null ? null : JpsPathUtil.urlToFile(url);
  }

  private static File[] toFiles(String[] urls) {
    File[] files = new File[urls.length];
    int i = 0;
    for (String url : urls) {
      files[i] = JpsPathUtil.urlToFile(url);
      i++;
    }
    return files;
  }

  public static final class State extends ScalaLibraryPropertiesStateSharedInIdeaAndJps {
  }
}
