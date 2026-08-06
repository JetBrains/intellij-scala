package org.jetbrains.jps.incremental.scala.model.impl;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.scala.model.LibrarySettings;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.util.JpsPathUtil;
import org.jetbrains.plugins.scala.project.ScalaLibraryPropertiesStateSharedInIdeaAndJps;

import java.nio.file.Path;
import java.util.Arrays;

public class LibrarySettingsImpl extends JpsElementBase<LibrarySettingsImpl> implements LibrarySettings {
  private final State myState;

  public LibrarySettingsImpl(State state) {
    myState = state;
  }

  @Override
  public Path[] getCompilerClasspath() {
    String[] classpath = myState.getCompilerClasspath();
    return classpath == null ? new Path[0] : toPaths(classpath);
  }

  @Override
  public @Nullable Path getCompilerBridgeJar() {
    String url = myState.getCompilerBridgeBinaryJar();
    return url == null ? null : JpsPathUtil.urlToNioPath(url);
  }

  @Override
  public Path[] getReplClasspath() {
    String[] classpath = myState.getReplClasspath();
    return classpath == null ? new Path[0] : toPaths(classpath);
  }

  private static Path[] toPaths(String[] urls) {
    return Arrays.stream(urls).map(JpsPathUtil::urlToNioPath).toArray(Path[]::new);
  }

  public static final class State extends ScalaLibraryPropertiesStateSharedInIdeaAndJps {
  }
}
