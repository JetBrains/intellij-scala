package org.jetbrains.jps.incremental.scala;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.scala.model.*;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author Pavel Fatin
 */
public class SettingsManager {
  public static final JpsElementChildRoleBase<GlobalSettings> GLOBAL_SETTINGS_ROLE = JpsElementChildRoleBase.create("scala global settings");
  public static final JpsElementChildRoleBase<ProjectSettings> PROJECT_SETTINGS_ROLE = JpsElementChildRoleBase.create("scala project settings");
  public static final JpsElementChildRoleBase<LibrarySettings> LIBRARY_SETTINGS_ROLE = JpsElementChildRoleBase.create("scala library settings");
  public static final JpsElementChildRoleBase<FacetSettings> FACET_SETTINGS_ROLE = JpsElementChildRoleBase.create("scala facet settings");

  public static GlobalSettings getGlobalSettings(JpsGlobal global) {
    GlobalSettings settings = global.getContainer().getChild(GLOBAL_SETTINGS_ROLE);
    return settings == null ? GlobalSettingsImpl.DEFAULT : settings;
  }

  public static void setGlobalSettings(JpsGlobal global, GlobalSettings settings) {
    global.getContainer().setChild(GLOBAL_SETTINGS_ROLE, settings);
  }

  public static ProjectSettings getProjectSettings(JpsProject project) {
    return project.getContainer().getChild(PROJECT_SETTINGS_ROLE);
  }

  public static void setProjectSettings(JpsProject project, ProjectSettings settings) {
    project.getContainer().setChild(PROJECT_SETTINGS_ROLE, settings);
  }

  @Nullable
  public static LibrarySettings getLibrarySettings(JpsModule library) {
    return library.getContainer().getChild(LIBRARY_SETTINGS_ROLE);
  }

  public static void setLibrarySettings(JpsModule module, LibrarySettings settings) {
    module.getContainer().setChild(LIBRARY_SETTINGS_ROLE, settings);
  }

  @Nullable
  public static FacetSettings getFacetSettings(@NotNull JpsModule module) {
    return module.getContainer().getChild(FACET_SETTINGS_ROLE);
  }

  public static void setFacetSettings(@NotNull JpsModule project, FacetSettings module) {
    project.getContainer().setChild(FACET_SETTINGS_ROLE, module);
  }
}
