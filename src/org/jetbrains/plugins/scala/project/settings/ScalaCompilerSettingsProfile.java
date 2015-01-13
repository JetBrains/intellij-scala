package org.jetbrains.plugins.scala.project.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Pavel Fatin
 */
// TODO This class is needed for the "imported" ScalaCompilerConfigurationPanel.
// TODO It's better to replace it with immutable case classes later.
public class ScalaCompilerSettingsProfile {
  private String myName;
  private List<String> myModuleNames = new ArrayList<String>();
  private ScalaCompilerSettings mySettings = new ScalaCompilerSettings();

  public ScalaCompilerSettingsProfile(String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }

  public void initFrom(ScalaCompilerSettingsProfile profile) {
    myName = profile.getName();
    mySettings = profile.getSettings();
    myModuleNames = new ArrayList<String>(profile.getModuleNames());
  }

  public List<String> getModuleNames() {
    return Collections.unmodifiableList(myModuleNames);
  }

  public void addModuleName(String name) {
    myModuleNames.add(name);
  }

  public void removeModuleName(String name) {
    myModuleNames.remove(name);
  }

  public ScalaCompilerSettings getSettings() {
    return mySettings;
  }

  public void setSettings(ScalaCompilerSettings settigns) {
    mySettings = settigns;
  }

  @Override
  public String toString() {
    return myName;
  }
}
