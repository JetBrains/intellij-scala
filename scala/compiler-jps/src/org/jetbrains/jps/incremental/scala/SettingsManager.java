package org.jetbrains.jps.incremental.scala;

import org.jetbrains.jps.incremental.scala.model.*;
import org.jetbrains.jps.incremental.scala.model.impl.GlobalSettingsImpl;
import org.jetbrains.jps.incremental.scala.model.impl.ProjectSettingsImpl;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.module.JpsModule;
import scala.Option;

import java.util.Set;

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

    JpsLibrary scalaSdk = null;
    for (JpsLibrary library : libraries) {
      if (library.getType() == ScalaLibraryType.getInstance()) {
        // TODO: workaround for SCL-17196, SCL-18166, SCL-18867
        //  see also the same workaround in org.jetbrains.plugins.scala.project.ScalaModuleSettings.apply
        if (library.getName().contains("scala3"))
          return Option.apply(library);
        else
          scalaSdk = library;
      }
    }
    return Option.apply(scalaSdk);
  }
}
