package org.jetbrains.jps.incremental.scala.model;

import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;

/**
 * @author Pavel Fatin
 */
public class GlobalSettingsImpl extends JpsElementBase<GlobalSettingsImpl> implements GlobalSettings {
  public static final GlobalSettings DEFAULT = new GlobalSettingsImpl(new State());

  private State myState;

  public GlobalSettingsImpl(State state) {
    myState = state;
  }

  public boolean isCompilationServerEnabled() {
    return myState.COMPILE_SERVER_ENABLED;
  }

  public int getCompilationServerPort() {
    return myState.COMPILE_SERVER_PORT;
  }

  @NotNull
  @Override
  public GlobalSettingsImpl createCopy() {
    return new GlobalSettingsImpl(XmlSerializerUtil.createCopy(myState));
  }

  @Override
  public void applyChanges(@NotNull GlobalSettingsImpl compilerSettings) {
    // do nothing
  }

  public static class State {
    public boolean COMPILE_SERVER_ENABLED = true;

    public int COMPILE_SERVER_PORT = 3200;
  }
}
