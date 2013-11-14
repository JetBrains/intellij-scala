package org.jetbrains.jps.incremental.scala.model;

import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;

/**
 * @author Pavel Fatin
 */
public class LibrarySettingsImpl extends JpsElementBase<LibrarySettingsImpl> implements LibrarySettings {
  private State myState;

  public LibrarySettingsImpl(State state) {
    myState = state;
  }

  public String[] getCompilerClasspath() {
    return myState.compilerClasspath;
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
    public String[] compilerClasspath;
  }
}
