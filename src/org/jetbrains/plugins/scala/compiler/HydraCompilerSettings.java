package org.jetbrains.plugins.scala.compiler;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
  * @author Maris Alexandru
  */
@State(
        name = "HydraSettings",
        storages = {@Storage("hydra.xml")}
)
class HydraCompilerSettings implements PersistentStateComponent<HydraCompilerSettings> {
  public boolean isHydraEnabled = false;

  public String hydraVersion = "";

  public HydraCompilerSettings getState() {
    return this;
  }
  public void loadState(HydraCompilerSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static HydraCompilerSettings getInstance() {
    return ServiceManager.getService(HydraCompilerSettings.class);
  }
}

