package org.jetbrains.plugins.scala.settings;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Ksenia.Sautina
 * @since 4/25/12
 */

@State(
    name = "ScalaProjectSettings",
    storages = {
        @Storage(file = "$WORKSPACE_FILE$"),
        @Storage(file = "$PROJECT_CONFIG_DIR$/scala_settings.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class ScalaProjectSettings  implements PersistentStateComponent<ScalaProjectSettings>, ExportableComponent {
  private int IMPLICIT_PARAMETERS_SEARCH_DEPTH = -1;

  private String BASE_PACKAGE = "";
  
  private boolean SEARCH_ALL_SYMBOLS = false;

  private boolean ENABLE_JAVA_TO_SCALA_CONVERSION = true;
  private boolean DONT_SHOW_CONVERSION_DIALOG = false;
  private boolean SHOW_IMPLICIT_CONVERSIONS = true;

  private boolean SHOW_ARGUMENTS_TO_BY_NAME_PARAMETERS = false;
  private boolean INCLUDE_BLOCK_EXPRESSIONS = false;
  private boolean INCLUDE_LITERALS = false;
  private boolean IGNORE_PERFORMANCE_TO_FIND_ALL_CLASS_NAMES = false;

  private boolean TREAT_DOC_COMMENT_AS_BLOCK_COMMENT = false;
  private boolean DISABLE_LANGUAGE_INJECTION = true;
  private boolean DISABLE_I18N = false;
  private boolean DONT_CACHE_COMPOUND_TYPES = false;
  private boolean AOT_COMPLETION = true;
  private boolean SCALA_CLASSES_PRIORITY = true;
  private boolean GENERATE_TOSTRING_WITH_FIELD_NAMES = false;
  private boolean USE_OLD_IMPLICIT_CONVERSION_ALG = false;

  //WORKSHEET
  private int OUTPUT_LIMIT = 35;
  private boolean IN_PROCESS_MODE = true;
  private boolean INTERACTIVE_MODE = true;
  private boolean USE_ECLIPSE_COMPATIBILITY = false;

  private Map<String, String> INTERPOLATED_INJECTION_MAPPING = new HashMap<String, String>();

  {
    INTERPOLATED_INJECTION_MAPPING.put("sql", "SQL");
    INTERPOLATED_INJECTION_MAPPING.put("sqlu", "SQL");
    INTERPOLATED_INJECTION_MAPPING.put("xml", "XML");
  }

  //colection type highlighting settings
  private int COLLECTION_TYPE_HIGHLIGHTING_LEVEL = 0;

  public static final int COLLECTION_TYPE_HIGHLIGHTING_ALL = 2;
  public static final int COLLECTION_TYPE_HIGHLIGHTING_NOT_QUALIFIED = 1;
  public static final int COLLECTION_TYPE_HIGHLIGHTING_NONE = 0;

  public static ScalaProjectSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ScalaProjectSettings.class);
  }

  public ScalaProjectSettings getState() {
    return this;
  }

  public void loadState(ScalaProjectSettings scalaProjectSettings) {
    XmlSerializerUtil.copyBean(scalaProjectSettings, this);
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile("scala_project_settings")};
  }

  @NotNull
  public String getPresentableName() {
    return "Scala Project Settings";
  }

  public int getImplicitParametersSearchDepth() {
    return IMPLICIT_PARAMETERS_SEARCH_DEPTH;
  }

  public void setImplicitParametersSearchDepth(int value) {
    IMPLICIT_PARAMETERS_SEARCH_DEPTH = value;
  }

  public int getOutputLimit() {
    return  OUTPUT_LIMIT;
  }

  public void setOutputLimit(int value) {
    OUTPUT_LIMIT = value;
  }

  public boolean isSearchAllSymbols() {
    return SEARCH_ALL_SYMBOLS;
  }

  public void setSearchAllSymbols(boolean value) {
    SEARCH_ALL_SYMBOLS = value;
  }

  public boolean isEnableJavaToScalaConversion() {
    return ENABLE_JAVA_TO_SCALA_CONVERSION;
  }

  public void setEnableJavaToScalaConversion(boolean value) {
    ENABLE_JAVA_TO_SCALA_CONVERSION = value;
  }

  public boolean isDontShowConversionDialog() {
    return DONT_SHOW_CONVERSION_DIALOG;
  }

  public void setDontShowConversionDialog(boolean value) {
    DONT_SHOW_CONVERSION_DIALOG = value;
  }

  public boolean isShowImplisitConversions() {
    return SHOW_IMPLICIT_CONVERSIONS;
  }

  public void setShowImplisitConversions(boolean value) {
    SHOW_IMPLICIT_CONVERSIONS = value;
  }

  public boolean isShowArgumentsToByNameParams() {
    return SHOW_ARGUMENTS_TO_BY_NAME_PARAMETERS;
  }

  public void setShowArgumentsToByNameParams(boolean value) {
    SHOW_ARGUMENTS_TO_BY_NAME_PARAMETERS = value;
  }

  public boolean isIncludeBlockExpressions() {
    return INCLUDE_BLOCK_EXPRESSIONS;
  }

  public void setIncludeBlockExpressions(boolean value) {
    INCLUDE_BLOCK_EXPRESSIONS = value;
  }

  public boolean isIncludeLiterals() {
    return INCLUDE_LITERALS;
  }

  public void setIncludeLiterals(boolean value) {
    INCLUDE_LITERALS = value;
  }

  public boolean isIgnorePerformance() {
    return IGNORE_PERFORMANCE_TO_FIND_ALL_CLASS_NAMES;
  }

  public void setIgnorePerformance(boolean value) {
    IGNORE_PERFORMANCE_TO_FIND_ALL_CLASS_NAMES = value;
  }

  public boolean isTreatDocCommentAsBlockComment() {
    return TREAT_DOC_COMMENT_AS_BLOCK_COMMENT;
  }

  public void setTreatDocCommentAsBlockComment(boolean value) {
    TREAT_DOC_COMMENT_AS_BLOCK_COMMENT = value;
  }

  public boolean isDontCacheCompoundTypes() {
      return DONT_CACHE_COMPOUND_TYPES;
  }

  public void setDontCacheCompoundTypes(boolean value) {
      DONT_CACHE_COMPOUND_TYPES = value;
  }

  public boolean isDisableLangInjection() {
    return DISABLE_LANGUAGE_INJECTION;
  }

  public void setDisableLangInjection(boolean value) {
    DISABLE_LANGUAGE_INJECTION = value;
  }

  public boolean isAotCompletion() {
    return AOT_COMPLETION;
  }

  public void setAotCOmpletion(boolean value) {
    AOT_COMPLETION = value;
  }

  public boolean isScalaPriority() {
    return SCALA_CLASSES_PRIORITY;
  }

  public void setScalaPriority(boolean value) {
    SCALA_CLASSES_PRIORITY = value;
  }

  public int getCollectionTypeHighlightingLevel() {
    return COLLECTION_TYPE_HIGHLIGHTING_LEVEL;
  }

  public void setCollectionTypeHighlightingLevel(int level) {
    this.COLLECTION_TYPE_HIGHLIGHTING_LEVEL = level;
  }
  
  public Map<String, String> getIntInjectionMapping() {
    return INTERPOLATED_INJECTION_MAPPING;
  }

  public String getBasePackage() {
    return BASE_PACKAGE;
  }

  public void setBasePackage(String name) {
    BASE_PACKAGE = name;
  }

  public void setIntInjectionMapping(Map<String, String> intInjectionMapping) {
    INTERPOLATED_INJECTION_MAPPING = intInjectionMapping;
  }

  public boolean isInProcessMode() {
    return IN_PROCESS_MODE;
  }

  public void setInProcessMode(boolean inProcess) {
    this.IN_PROCESS_MODE = inProcess;
  }

  public boolean isInteractiveMode() { return INTERACTIVE_MODE; }

  public void setInteractiveMode(boolean interactiveMode) {
    INTERACTIVE_MODE = interactiveMode;
  }

  public boolean isUseEclipseCompatibility() {
    return USE_ECLIPSE_COMPATIBILITY;
  }

  public void setUseEclipseCompatibility(boolean USE_ECLIPSE_COMPATIBILITY) {
    this.USE_ECLIPSE_COMPATIBILITY = USE_ECLIPSE_COMPATIBILITY;
  }

  public boolean isGenerateToStringWithFieldNames() { return GENERATE_TOSTRING_WITH_FIELD_NAMES; }

  public void setGenerateToStringWithFieldNames(boolean generateToStringWithFieldNames) {
    this.GENERATE_TOSTRING_WITH_FIELD_NAMES = generateToStringWithFieldNames;
  }

  public boolean isUseOldImplicitConversionAlg() {
    return USE_OLD_IMPLICIT_CONVERSION_ALG;
  }

  public void setUseOldImplicitConversionAlg(boolean useOldImplicitConversionAlg) {
    USE_OLD_IMPLICIT_CONVERSION_ALG = useOldImplicitConversionAlg;
  }
}
