package org.jetbrains.jps.incremental.scala.model;

import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;

import java.nio.file.Paths;

/**
 * @author Maris Alexandru
 */
public class HydraSettingsImpl extends JpsElementBase<HydraSettingsImpl> implements HydraSettings{
  public static final HydraSettings DEFAULT = new HydraSettingsImpl(new State());

  private final State state;

  public HydraSettingsImpl(State state) {
    this.state = state;
  }

  @Override
  public boolean isHydraEnabled() { return state.isHydraEnabled; }

  @Override
  public String getHydraVersion() {
    return state.hydraVersion;
  }

  @Override
  public String getNumberOfCores() {
    return state.noOfCores;
  }

  @Override
  public String getHydraStorePath() { return Paths.get(state.hydraStorePath).toString(); }

  @Override
  public String getSourcePartitioner() { return state.sourcePartitioner; }

  @Override
  public String getProjectRoot() { return Paths.get(state.projectRoot).toString(); }

  @NotNull
  @Override
  public HydraSettingsImpl createCopy() {
    return new HydraSettingsImpl(XmlSerializerUtil.createCopy(state));
  }

  @Override
  public void applyChanges(@NotNull HydraSettingsImpl hydraSettings) {
    //do nothing
  }

  public static class State {
    public boolean isHydraEnabled = false;
    public String hydraVersion = "";
    public String noOfCores = Integer.toString((int) Math.ceil(Runtime.getRuntime().availableProcessors()/2D));
    public String hydraStorePath = "";
    public String sourcePartitioner = "";
    public String projectRoot = "";
  }
}
