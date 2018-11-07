package org.jetbrains.jps.incremental.scala.hydra;

import org.jetbrains.jps.incremental.scala.hydra.model.HydraSettings;
import org.jetbrains.jps.incremental.scala.hydra.model.GlobalHydraSettings;
import org.jetbrains.jps.incremental.scala.hydra.model.HydraSettingsImpl;
import org.jetbrains.jps.incremental.scala.hydra.model.GlobalHydraSettingsImpl;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;

public class HydraSettingsManager {
    public static final JpsElementChildRoleBase<HydraSettings> HYDRA_SETTINGS_ROLE = JpsElementChildRoleBase.create("scala hydra settings");
    public static final JpsElementChildRoleBase<GlobalHydraSettings> GLOBAL_HYDRA_SETTINGS_ROLE = JpsElementChildRoleBase.create("hydra global settings");


    public static HydraSettings getHydraSettings(JpsProject project) {
        HydraSettings settings = project.getContainer().getChild(HYDRA_SETTINGS_ROLE);
        return  settings == null ? HydraSettingsImpl.DEFAULT : settings;
    }

    public static void setHydraSettings(JpsProject project, HydraSettings hydraSettings) {
        project.getContainer().setChild(HYDRA_SETTINGS_ROLE, hydraSettings);
    }

    public static GlobalHydraSettings getGlobalHydraSettings(JpsGlobal global) {
        GlobalHydraSettings settings = global.getContainer().getChild(GLOBAL_HYDRA_SETTINGS_ROLE);
        return settings == null ? GlobalHydraSettingsImpl.DEFAULT : settings;
    }

    public static void setGlobalHydraSettings(JpsGlobal global, GlobalHydraSettings settings) {
        global.getContainer().setChild(GLOBAL_HYDRA_SETTINGS_ROLE, settings);
    }

}
