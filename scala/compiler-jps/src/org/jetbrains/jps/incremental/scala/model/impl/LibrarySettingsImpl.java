package org.jetbrains.jps.incremental.scala.model.impl;

import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.scala.model.LibrarySettings;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;

public class LibrarySettingsImpl extends JpsElementBase<LibrarySettingsImpl> implements LibrarySettings {
  private final State myState;

  public LibrarySettingsImpl(State state) {
    myState = state;
  }

  @Override
  public File[] getCompilerClasspath() {
    String[] classpath = myState.compilerClasspath;
    return classpath == null ? new File[0] : toFiles(classpath);
  }

  @Override
  public @Nullable File getCompilerBridgeJar() {
    String url = myState.compilerBridgeJar;
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

  @NotNull
  @Override
  public LibrarySettingsImpl createCopy() {
    return new LibrarySettingsImpl(XmlSerializerUtil.createCopy(myState));
  }

  @Override
  public void applyChanges(@NotNull LibrarySettingsImpl settings) {
    // do nothing
  }

  /**
   * Structure should be same as in {@code org.jetbrains.plugins.scala.project.ScalaLibraryPropertiesState}<br>
   */
  public static class State {
    @Tag("compiler-classpath")
    @XCollection(elementName = "root", valueAttributeName = "url")
    public String[] compilerClasspath;

    @Tag("compiler-bridge-binary-jar")
    public String compilerBridgeJar;
  }
}
