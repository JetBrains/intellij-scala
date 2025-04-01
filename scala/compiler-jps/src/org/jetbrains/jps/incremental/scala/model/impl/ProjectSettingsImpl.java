package org.jetbrains.jps.incremental.scala.model.impl;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.scala.model.CompilerSettings;
import org.jetbrains.jps.incremental.scala.model.ProjectSettings;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType;

import java.util.HashMap;
import java.util.Map;

public class ProjectSettingsImpl extends JpsElementBase<ProjectSettingsImpl> implements ProjectSettings {

  // TODO change the default value if separate main/test modules will be disabled by default
  public static final Boolean defaultSeparateMainTestModules = true;

  public static final ProjectSettingsImpl DEFAULT =
      new ProjectSettingsImpl(
              IncrementalityType.SBT,
              CompilerSettingsImpl.DEFAULT,
              new HashMap<>(),
              new HashMap<>(),
              new HashMap<>()
      );

  private final IncrementalityType myIncrementalityType;
  private final Map<String, Boolean> myExternalRootPathToSeparateMainTestModules;
  private final CompilerSettingsImpl myDefaultSettings;
  private final Map<String, CompilerSettingsImpl> myProfileToSettings;
  private final Map<String, String> myModuleToProfile;

  public ProjectSettingsImpl(IncrementalityType incrementalityType,
                             CompilerSettingsImpl defaultSettings,
                             Map<String, Boolean> separateProdTestSources,
                             Map<String, CompilerSettingsImpl> profileToSettings,
                             Map<String, String> moduleToProfile) {
    myIncrementalityType = incrementalityType;
    myExternalRootPathToSeparateMainTestModules = separateProdTestSources;
    myDefaultSettings = defaultSettings;
    myProfileToSettings = profileToSettings;
    myModuleToProfile = moduleToProfile;
  }

  @Override
  public IncrementalityType getIncrementalityType() {
    return myIncrementalityType;
  }

  @Override
  public CompilerSettings getCompilerSettings(ModuleChunk chunk) {
    String module = chunk.representativeTarget().getModule().getName();
    String profile = myModuleToProfile.get(module);
    return profile == null ? myDefaultSettings : myProfileToSettings.get(profile);
  }

  @Override
  public Boolean getExternalRootPathToSeparateMainTestModules(@Nullable String externalRootPath) {
    return (externalRootPath == null || !myExternalRootPathToSeparateMainTestModules.containsKey(externalRootPath))
            ? defaultSeparateMainTestModules
            : myExternalRootPathToSeparateMainTestModules.get(externalRootPath);
  }
}
