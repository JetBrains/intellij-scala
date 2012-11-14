package org.jetbrains.jps.incremental.scala;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.scala.model.FacetSettings;
import org.jetbrains.jps.incremental.scala.model.ProjectSettings;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author Pavel Fatin
 */
public class SettingsManager {
  public static final JpsElementChildRoleBase<ProjectSettings> PROJECT_SETTINGS_ROLE = JpsElementChildRoleBase.create("scala compiler settings");
  public static final JpsElementChildRoleBase<FacetSettings> FACET_SETTINGS_ROLE = JpsElementChildRoleBase.create("scala facet settings");

  @Nullable
  public static ProjectSettings getProjectSettings(@NotNull JpsProject project) {
    return project.getContainer().getChild(PROJECT_SETTINGS_ROLE);
  }

  public static void setProjectSettings(@NotNull JpsProject project, ProjectSettings settings) {
    project.getContainer().setChild(PROJECT_SETTINGS_ROLE, settings);
  }

  @Nullable
  public static FacetSettings getFacetSettings(@NotNull JpsModule module) {
    return module.getContainer().getChild(FACET_SETTINGS_ROLE);
  }

  public static void setFacetSettings(@NotNull JpsModule project, FacetSettings module) {
    project.getContainer().setChild(FACET_SETTINGS_ROLE, module);
  }
}
