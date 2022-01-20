package org.jetbrains.jps.incremental.scala.model.impl;

import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.scala.model.LibrarySettings;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;

/**
 * @author Pavel Fatin
 */
public class LibrarySettingsImpl extends JpsElementBase<LibrarySettingsImpl> implements LibrarySettings {
  private State myState;

  public LibrarySettingsImpl(State state) {
    myState = state;
  }

  public File[] getCompilerClasspath() {
    String[] classpath = myState.compilerClasspath;
    return classpath == null ? new File[0] : toFiles(classpath);
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
  public void applyChanges(@NotNull LibrarySettingsImpl facetSettings) {
    // do nothing
  }

  public static class State {
    @SuppressWarnings("deprecation")
    @Tag("compiler-classpath")
    @AbstractCollection(surroundWithTag = false, elementTag = "root", elementValueAttribute = "url")
    public String[] compilerClasspath;
  }
}
