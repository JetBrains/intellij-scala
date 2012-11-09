package org.jetbrains.jps.incremental.scala.model;

import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;

/**
 * @author Pavel Fatin
 */
public class CompilerSettingsImpl extends JpsElementBase<CompilerSettingsImpl> implements CompilerSettings {
  private State myState;

  public CompilerSettingsImpl(State state) {
    myState = state;
  }

  public boolean isScalaFirst() {
    return myState.SCALAC_BEFORE;
  }

  @NotNull
  @Override
  public CompilerSettingsImpl createCopy() {
    return new CompilerSettingsImpl(XmlSerializerUtil.createCopy(myState));
  }

  @Override
  public void applyChanges(@NotNull CompilerSettingsImpl compilerSettings) {
    // do nothing
  }

  public static class State {
    public boolean SCALAC_BEFORE = true;
  }
}
