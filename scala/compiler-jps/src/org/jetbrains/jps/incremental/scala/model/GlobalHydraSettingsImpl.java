package org.jetbrains.jps.incremental.scala.model;

import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author Maris Alexandru
 */
public class GlobalHydraSettingsImpl extends JpsElementBase<GlobalHydraSettingsImpl> implements GlobalHydraSettings {
  public static final GlobalHydraSettings DEFAULT = new GlobalHydraSettingsImpl(new State());

  private final State myState;

  public GlobalHydraSettingsImpl(State state) {
    this.myState = state;
  }

  @Override
  public boolean containsArtifactsFor(String scalaVersion, String hydraVersion) {
    return myState.globalArtifactPaths.containsKey(scalaVersion + "_" + hydraVersion);
  }

  public List<String> getArtifactsFor(String scalaVersion, String hydraVersion) {
    return myState.globalArtifactPaths.getOrDefault(scalaVersion + "_" + hydraVersion, new LinkedList<>());
  }

  @NotNull
  @Override
  public GlobalHydraSettingsImpl createCopy() {
    return new GlobalHydraSettingsImpl(XmlSerializerUtil.createCopy(myState));
  }

  @Override
  public void applyChanges(@NotNull GlobalHydraSettingsImpl hydraSettings) {
    //do nothing
  }

  public static class State {
    public Map<String, List<String>> globalArtifactPaths = new HashMap();
  }
}
