package org.jetbrains.plugins.scala.settings;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.scala.codeInspection.collections.OperationOnCollectionInspectionBase;

@State(
        name = "ScalaApplicationSettings",
        storages = {@Storage(ScalaApplicationSettings.STORAGE_FILE_NAME)},
        reportStatistic = true,
        category = SettingsCategory.CODE
)
public class ScalaApplicationSettings implements PersistentStateComponent<ScalaApplicationSettings> {
  public static final String STORAGE_FILE_NAME = "scala_config.xml";

  // TODO Don't save these values as we now rely on the code style instead
  public boolean INTRODUCE_VARIABLE_EXPLICIT_TYPE = true;
  public boolean INTRODUCE_FIELD_EXPLICIT_TYPE = true;
  public boolean INTRODUCE_FIELD_IS_VAR = false;
  public boolean INTRODUCE_FIELD_REPLACE_ALL = false;

  public boolean IGNORE_SETTINGS_CHECK = false;

  public boolean COPY_SCALADOC = true;

  // TODO See the comment above
  public enum ReturnTypeLevel {ADD, REMOVE, BY_CODE_STYLE}
  public ReturnTypeLevel SPECIFY_RETURN_TYPE_EXPLICITLY = ReturnTypeLevel.BY_CODE_STYLE;

  public boolean INTRODUCE_PARAMETER_CREATE_DEFAULT = true;
  public boolean ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY = false;
  public boolean ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY_METHODS = false;
  public boolean OPTIMIZE_IMPORTS_ON_THE_FLY = false;

  public boolean SHOW_IMPORT_POPUP_CLASSES = true;
  public boolean SHOW_IMPORT_POPUP_STATIC_METHODS = true;
  public boolean SHOW_IMPORT_POPUP_CONVERSIONS = true;
  public boolean SHOW_IMPORT_POPUP_IMPLICITS = true;
  public boolean SHOW_IMPORT_POPUP_EXTENSION_METHODS = true;

  @ReportValue
  public int ADD_IMPORTS_ON_PASTE = CodeInsightSettings.ASK;
  public boolean RENAME_SEARCH_IN_COMMENTS_AND_STRINGS = false;
  public boolean RENAME_SEARCH_IN_NON_CODE_FILES = false;

  public String INTRODUCE_FIELD_VISIBILITY = "";

  public boolean ADD_OVERRIDE_TO_IMPLEMENTED = true;

  public boolean RENAME_COMPANION_MODULE = true;

  public boolean INTRODUCE_FIELD_INITIALIZE_IN_DECLARATION = true;

  public boolean INDENT_PASTED_LINES_AT_CARET = true;

  public boolean INSERT_MULTILINE_QUOTES = true;

  public boolean WRAP_SINGLE_EXPRESSION_BODY = true;

  public boolean DELETE_CLOSING_BRACE = true;

  public boolean HANDLE_BLOCK_BRACES_INSERTION_AUTOMATICALLY = true;

  public boolean HANDLE_BLOCK_BRACES_REMOVAL_AUTOMATICALLY = false;

  public boolean MOVE_COMPANION = true;

  public boolean UPGRADE_TO_INTERPOLATED = true;

  public boolean SUGGEST_TYPE_AWARE_HIGHLIGHTING_ENABLED = false;

  //MISC
  public boolean ASK_USE_LATEST_PLUGIN_BUILDS = true;
  public boolean ASK_PLATFORM_UPDATE = true;
  public enum pluginBranch {Release, EAP, Nightly}

  private String[] INSPECTION_LIKE_OPTION_CLASSES = OperationOnCollectionInspectionBase.likeOptionClassesDefault();
  private String[] INSPECTION_LIKE_COLLECTION_CLASSES = OperationOnCollectionInspectionBase.likeCollectionClassesDefault();

  public boolean SUGGEST_IN_EDITOR_DOC_RENDERING = true;

  public boolean SUGGEST_AUTOBRACE_INSERTION = true;

  public boolean SUGGEST_LEGACY_IMPORT_LAYOUT = true;

  // X-Ray Mode
  public boolean XRAY_DOUBLE_PRESS_AND_HOLD = true;
  public boolean XRAY_PRESS_AND_HOLD = false;
  public boolean XRAY_SHOW_PARAMETER_HINTS = true;
  public boolean XRAY_SHOW_ARGUMENT_HINTS = true;
  public boolean XRAY_SHOW_TYPE_HINTS = true;
  public boolean XRAY_SHOW_MEMBER_VARIABLE_HINTS = true;
  public boolean XRAY_SHOW_LOCAL_VARIABLE_HINTS = true;
  public boolean XRAY_SHOW_METHOD_RESULT_HINTS = true;
  public boolean XRAY_SHOW_LAMBDA_PARAMETER_HINTS = true;
  public boolean XRAY_SHOW_LAMBDA_PLACEHOLDER_HINTS = true;
  public boolean XRAY_SHOW_VARIABLE_PATTERN_HINTS = true;
  public boolean XRAY_SHOW_METHOD_CHAIN_HINTS = true;
  public boolean XRAY_SHOW_IMPLICIT_HINTS = true;
  public boolean XRAY_SHOW_INDENT_GUIDES = true;
  public boolean XRAY_SHOW_METHOD_SEPARATORS = false;

  public boolean SUGGEST_XRAY_MODE = true;

  @TestOnly
  public transient boolean PRECISE_TEXT = false;

  @Override
  public ScalaApplicationSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull ScalaApplicationSettings scalaApplicationSettings) {
    XmlSerializerUtil.copyBean(scalaApplicationSettings, this);
  }

  public static ScalaApplicationSettings getInstance() {
    return ApplicationManager.getApplication().getService(ScalaApplicationSettings.class);
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
