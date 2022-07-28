package org.jetbrains.jps.incremental.scala.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.scala.model.CompilerSettings;
import org.jetbrains.jps.incremental.scala.model.ProjectSettings;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType;

import java.util.HashMap;
import java.util.Map;

public class ProjectSettingsImpl extends JpsElementBase<ProjectSettingsImpl> implements ProjectSettings {

  public static final ProjectSettingsImpl DEFAULT =
      new ProjectSettingsImpl(
              IncrementalityType.IDEA,
              CompilerSettingsImpl.DEFAULT,
              new HashMap<>(),
              new HashMap<>()
      );

  private final IncrementalityType myIncrementalityType;
  private final CompilerSettingsImpl myDefaultSettings;
  private final Map<String, CompilerSettingsImpl> myProfileToSettings;
  private final Map<String, String> myModuleToProfile;

  public ProjectSettingsImpl(IncrementalityType incrementalityType,
                             CompilerSettingsImpl defaultSettings,
                             Map<String, CompilerSettingsImpl> profileToSettings,
                             Map<String, String> moduleToProfile) {
    myIncrementalityType = incrementalityType;
    myDefaultSettings = defaultSettings;
    myProfileToSettings = profileToSettings;
    myModuleToProfile = moduleToProfile;
  }

  @NotNull
  @Override
  public ProjectSettingsImpl createCopy() {
    CompilerSettingsImpl defaultSettings = myDefaultSettings.createCopy();

    Map<String, CompilerSettingsImpl> profileToSettings = new HashMap<>();
    for (Map.Entry<String, CompilerSettingsImpl> entry : myProfileToSettings.entrySet()) {
      profileToSettings.put(entry.getKey(), entry.getValue().createCopy());
    }

    HashMap<String, String> moduleToProfile = new HashMap<>(myModuleToProfile);

    return new ProjectSettingsImpl(myIncrementalityType, defaultSettings, profileToSettings, moduleToProfile);
  }

  @Override
  public void applyChanges(@NotNull ProjectSettingsImpl modified) {
    // do nothing
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
}
