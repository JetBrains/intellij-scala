package org.jetbrains.plugins.scala.settings;

/**
 * @author ilyas
 */

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.plugins.scala.codeInspection.collections.OperationOnCollectionInspectionBase;

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

  public boolean UPGRADE_TO_INTERPOLATED = true;

  //MISC
  public boolean ASK_USE_LATEST_PLUGIN_BUILDS = true;
  public boolean ASK_PLATFORM_UPDATE = true;
  public enum pluginBranch {Release, EAP, Nightly}

  private String[] INSPECTION_LIKE_OPTION_CLASSES = OperationOnCollectionInspectionBase.likeOptionClassesDefault();
  private String[] INSPECTION_LIKE_COLLECTION_CLASSES = OperationOnCollectionInspectionBase.likeCollectionClassesDefault();

  public ScalaApplicationSettings getState() {
    return this;
  }

  public void loadState(ScalaApplicationSettings scalaApplicationSettings) {
    XmlSerializerUtil.copyBean(scalaApplicationSettings, this);
  }

  public static ScalaApplicationSettings getInstance() {
    return ServiceManager.getService(ScalaApplicationSettings.class);
  }

  public String[] getLikeOptionClasses() {
    return INSPECTION_LIKE_OPTION_CLASSES;
  }

  public void setLikeOptionClasses(String[] patterns) {
    INSPECTION_LIKE_OPTION_CLASSES = patterns;
  }

  public String[] getLikeCollectionClasses() {
    return INSPECTION_LIKE_COLLECTION_CLASSES;
  }

  public void setLikeCollectionClasses(String[] patterns) {
    INSPECTION_LIKE_COLLECTION_CLASSES = patterns;
  }

}
