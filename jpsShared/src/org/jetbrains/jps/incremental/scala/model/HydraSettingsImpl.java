package org.jetbrains.jps.incremental.scala.model;

import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Maris Alexandru
 */
public class HydraSettingsImpl extends JpsElementBase<HydraSettingsImpl> implements HydraSettings{
  public static final HydraSettings DEFAULT = new HydraSettingsImpl(new State());

  private final State myState;

  public HydraSettingsImpl(State state) {
    this.myState = state;
  }

  @Override
  public boolean isHydraEnabled() { return myState.isHydraEnabled; }

  @Override
  public String getHydraVersion() {
    return myState.hydraVersion;
  }

  @NotNull
  @Override
  public HydraSettingsImpl createCopy() {
    return new HydraSettingsImpl(XmlSerializerUtil.createCopy(myState));
  }

  @Override
  public void applyChanges(@NotNull HydraSettingsImpl hydraSettings) {
    //do nothing
  }

  public static class State {
    public boolean isHydraEnabled = false;
    public String hydraVersion = "";
  }
}
