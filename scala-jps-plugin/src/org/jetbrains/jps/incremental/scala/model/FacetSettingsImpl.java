package org.jetbrains.jps.incremental.scala.model;

import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;

/**
 * @author Pavel Fatin
 */
public class FacetSettingsImpl extends JpsElementBase<FacetSettingsImpl> implements FacetSettings {
  private State myState;

  public FacetSettingsImpl(State state) {
    myState = state;
  }

  public LibraryLevel getCompilerLibraryLevel() {
    return myState.compilerLibraryLevel;
  }

  public String getCompilerLibraryName() {
    return myState.compilerLibraryName;
  }

  public boolean isFscEnabled() {
    return myState.fsc;
  }

  @NotNull
  @Override
  public FacetSettingsImpl createCopy() {
    return new FacetSettingsImpl(XmlSerializerUtil.createCopy(myState));
  }

  @Override
  public void applyChanges(@NotNull FacetSettingsImpl facetSettings) {
    // do nothing
  }

  public static class State {
    public LibraryLevel compilerLibraryLevel;

    public String compilerLibraryName;

    public boolean fsc;
  }
}
