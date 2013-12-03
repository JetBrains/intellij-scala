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

  public boolean isCompileServerEnabled() {
    return myState.COMPILE_SERVER_ENABLED;
  }

  public int getCompileServerPort() {
    return myState.COMPILE_SERVER_PORT;
  }

  public String getCompileServerSdk() {
    return myState.COMPILE_SERVER_SDK;
  }

  public IncrementalType getIncrementalType() {
    return IncrementalType.valueOf(myState.INCREMENTAL_TYPE);
  }

  public Order getCompileOrder() {
    return Order.valueOf(myState.COMPILE_ORDER);
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

    public String COMPILE_SERVER_SDK;

    public String INCREMENTAL_TYPE = "SBT";

    public String COMPILE_ORDER = "Mixed";
  }
}
