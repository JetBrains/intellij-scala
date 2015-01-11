package org.jetbrains.jps.incremental.scala.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.model.ex.JpsElementBase;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Pavel Fatin
 */
public class ProjectSettingsImpl extends JpsElementBase<ProjectSettingsImpl> implements ProjectSettings {
  public static final ProjectSettingsImpl DEFAULT = new ProjectSettingsImpl(CompilerSettingsImpl.DEFAULT,
      new HashMap<String, CompilerSettingsImpl>(), new HashMap<String, String>());

  private CompilerSettingsImpl myDefaultSettings;

  private Map<String, CompilerSettingsImpl> myProfileToSettings;

  private Map<String, String> myModuleToProfile;


  public ProjectSettingsImpl(CompilerSettingsImpl defaultSettings, Map<String, CompilerSettingsImpl> profileToSettings, Map<String, String> moduleToProfile) {
    myDefaultSettings = defaultSettings;
    myProfileToSettings = profileToSettings;
    myModuleToProfile = moduleToProfile;
  }

  @NotNull
  @Override
  public ProjectSettingsImpl createCopy() {
    CompilerSettingsImpl defaultSettings = myDefaultSettings.createCopy();

    Map<String, CompilerSettingsImpl> profileToSettings = new HashMap<String, CompilerSettingsImpl>();
    for (Map.Entry<String, CompilerSettingsImpl> entry : myProfileToSettings.entrySet()) {
      profileToSettings.put(entry.getKey(), entry.getValue().createCopy());
    }

    HashMap<String, String> moduleToProfile = new HashMap<String, String>(myModuleToProfile);

    return new ProjectSettingsImpl(defaultSettings, profileToSettings, moduleToProfile);
  }

  @Override
  public void applyChanges(@NotNull ProjectSettingsImpl modified) {
    // do nothing
  }

  @Override
  public CompilerSettings getDefaultSettings() {
    return myDefaultSettings;
  }

  @Override
  public CompilerSettings getCompilerSettings(ModuleChunk chunk) {
    String module = chunk.representativeTarget().getModule().getName();
    String profile = myModuleToProfile.get(module);
    return profile == null ? myDefaultSettings : myProfileToSettings.get(profile);
  }
}
