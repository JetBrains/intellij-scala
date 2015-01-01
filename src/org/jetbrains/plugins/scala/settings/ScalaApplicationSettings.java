package org.jetbrains.plugins.scala.settings;

/**
 * @author ilyas
 */

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.plugins.scala.components.ScalaPluginUpdater;

@State(
    name = "ScalaApplicationSettings",
    storages = {
    @Storage(
        id = "scala_config",
        file = "$APP_CONFIG$/scala_config.xml"
    )}
)
public class ScalaApplicationSettings implements PersistentStateComponent<ScalaApplicationSettings> {

  public boolean INTRODUCE_VARIABLE_EXPLICIT_TYPE = true;
  public boolean INTRODUCE_VARIABLE_IS_VAR = false;
  public boolean INTRODUCE_FIELD_EXPLICIT_TYPE = true;
  public boolean INTRODUCE_FIELD_IS_VAR = false;
  public boolean INTRODUCE_FIELD_REPLACE_ALL = false;

  public boolean IGNORE_SETTINGS_CHECK = false;

  public boolean SPECIFY_RETURN_TYPE_EXPLICITLY = true;
  public boolean INTRODUCE_PARAMETER_CREATE_DEFAULT = true;
  public boolean ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY = false;
  public boolean OPTIMIZE_IMPORTS_ON_THE_FLY = false;

  public int ADD_IMPORTS_ON_PASTE = CodeInsightSettings.ASK;
  public boolean RENAME_SEARCH_IN_COMMENTS_AND_STRINGS = false;
  public boolean RENAME_SEARCH_IN_NON_CODE_FILES = false;

  public enum VisibilityLevel {DEFAULT, PROTECTED, PRIVATE}
  public VisibilityLevel INTRODUCE_FIELD_VISIBILITY = VisibilityLevel.DEFAULT;

  public boolean ADD_OVERRIDE_TO_IMPLEMENTED = true;

  public boolean RENAME_COMPANION_MODULE = true;

  public boolean INTRODUCE_FIELD_INITIALIZE_IN_DECLARATION = true;

  public boolean INSERT_MULTILINE_QUOTES = true;

  public boolean MOVE_COMPANION = true;

  //MISC
  public boolean ASK_USE_LATEST_PLUGIN_BUILDS = true;
  public enum pluginBranch {Release, EAP, Nightly}

  public pluginBranch getScalaPluginBranch() {
    if (ScalaPluginUpdater.pluginIsEap())
      this.SCALA_PLUGIN_BRANCH = pluginBranch.EAP;
    else if (ScalaPluginUpdater.pluginIsNightly())
      this.SCALA_PLUGIN_BRANCH = pluginBranch.Nightly;
    else
      this.SCALA_PLUGIN_BRANCH = pluginBranch.Release;
    return this.SCALA_PLUGIN_BRANCH;
  }

  public void setScalaPluginBranch(pluginBranch SCALA_PLUGIN_BRANCH) {
    // update hack - set plugin version to 0 when downgrading
    if (getScalaPluginBranch().compareTo(SCALA_PLUGIN_BRANCH) > 0) {
      ScalaPluginUpdater.patchPluginVersion();
    }
    this.SCALA_PLUGIN_BRANCH = SCALA_PLUGIN_BRANCH;
    ScalaPluginUpdater.doUpdatePluginHosts(SCALA_PLUGIN_BRANCH);
    UpdateChecker.updateAndShowResult();
  }

  public pluginBranch SCALA_PLUGIN_BRANCH = pluginBranch.Release;

  public ScalaApplicationSettings getState() {
    return this;
  }

  public void loadState(ScalaApplicationSettings scalaApplicationSettings) {
    XmlSerializerUtil.copyBean(scalaApplicationSettings, this);
  }

  public static ScalaApplicationSettings getInstance() {
    return ServiceManager.getService(ScalaApplicationSettings.class);
  }

}
