package org.jetbrains.jps.incremental.scala;

import org.jetbrains.jps.incremental.scala.model.*;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.module.JpsModule;
import scala.Option;

import java.util.Set;

/**
 * @author Pavel Fatin
 */
public class SettingsManager {
  public static final JpsElementChildRoleBase<GlobalSettings> GLOBAL_SETTINGS_ROLE = JpsElementChildRoleBase.create("scala global settings");
  public static final JpsElementChildRoleBase<ProjectSettings> PROJECT_SETTINGS_ROLE = JpsElementChildRoleBase.create("scala project settings");
  public static final JpsElementChildRoleBase<LibrarySettings> LIBRARY_SETTINGS_ROLE = JpsElementChildRoleBase.create("scala library settings");

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

  public static Option<JpsLibrary> getScalaSdk(JpsModule module) {
    Set<JpsLibrary> libraries = JpsJavaExtensionService.dependencies(module)
            .recursivelyExportedOnly()
            .getLibraries();

    for (JpsLibrary library : libraries) {
      if (library.getType() == ScalaLibraryType.getInstance()) {
        return Option.apply(library);
      }
    }
    return Option.empty();
  }
}
