package org.jetbrains.jps.incremental.scala;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.scala.model.*;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.Collection;

/**
 * @author Pavel Fatin
 */
public class SettingsManager {
  public static final JpsElementChildRoleBase<GlobalSettings> GLOBAL_SETTINGS_ROLE = JpsElementChildRoleBase.create("scala global settings");
  public static final JpsElementChildRoleBase<ProjectSettings> PROJECT_SETTINGS_ROLE = JpsElementChildRoleBase.create("scala project settings");
  public static final JpsElementChildRoleBase<LibrarySettings> LIBRARY_SETTINGS_ROLE = JpsElementChildRoleBase.create("scala library settings");
  public static final JpsElementChildRoleBase<HydraSettings> HYDRA_SETTINGS_ROLE = JpsElementChildRoleBase.create("scala hydra settings");
  public static final JpsElementChildRoleBase<GlobalHydraSettings> GLOBAL_HYDRA_SETTINGS_ROLE = JpsElementChildRoleBase.create("hydra global settings");

  public static GlobalSettings getGlobalSettings(JpsGlobal global) {
    GlobalSettings settings = global.getContainer().getChild(GLOBAL_SETTINGS_ROLE);
    return settings == null ? GlobalSettingsImpl.DEFAULT : settings;
  }

  public static void setGlobalSettings(JpsGlobal global, GlobalSettings settings) {
    global.getContainer().setChild(GLOBAL_SETTINGS_ROLE, settings);
  }

  public static ProjectSettings getProjectSettings(JpsProject project) {
    ProjectSettings settings = project.getContainer().getChild(PROJECT_SETTINGS_ROLE);
    return settings == null ? ProjectSettingsImpl.DEFAULT : settings;
  }

  public static void setProjectSettings(JpsProject project, ProjectSettings settings) {
    project.getContainer().setChild(PROJECT_SETTINGS_ROLE, settings);
  }

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

  public static boolean hasScalaSdk(JpsModule module) {
    return getScalaSdk(module) != null;
  }

  @Nullable
  public static JpsLibrary getScalaSdk(JpsModule module) {
    for (JpsLibrary library : libraryDependenciesIn(module)) {
      if (library.getType() == ScalaLibraryType.getInstance()) {
        return library;
      }
    }
    return null;
  }

  public static Collection<JpsLibrary> libraryDependenciesIn(JpsModule module) {
    return JpsJavaExtensionService.dependencies(module).recursivelyExportedOnly().getLibraries();
  }
}
