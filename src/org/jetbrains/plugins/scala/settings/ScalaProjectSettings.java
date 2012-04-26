package org.jetbrains.plugins.scala.settings;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;

import java.io.File;

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
  private ScalaCodeStyleSettings scalaSettings =
      CodeStyleSettingsManager.getInstance().getCurrentSettings().getCustomSettings(ScalaCodeStyleSettings.class);

  private boolean ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = scalaSettings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
  private int CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = scalaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND;
  private boolean ADD_IMPORT_MOST_CLOSE_TO_REFERENCE = scalaSettings.ADD_IMPORT_MOST_CLOSE_TO_REFERENCE;
  private boolean ADD_FULL_QUALIFIED_IMPORTS = scalaSettings.ADD_FULL_QUALIFIED_IMPORTS;
  private boolean IMPORTS_MEMBERS_USING_UNDERSCORE = scalaSettings.IMPORTS_MEMBERS_USING_UNDERSCORE;

  private boolean SEARCH_ALL_SYMBOLS = scalaSettings.SEARCH_ALL_SYMBOLS;
  private boolean ENABLE_JAVA_TO_SCALA_CONVERSION = scalaSettings.ENABLE_JAVA_TO_SCALA_CONVERSION;
  private boolean DONT_SHOW_CONVERSION_DIALOG = scalaSettings.DONT_SHOW_CONVERSION_DIALOG;

  private boolean SHOW_IMPLICIT_CONVERSIONS = scalaSettings.SHOW_IMPLICIT_CONVERSIONS;
  private boolean SHOW_ARGUMENTS_TO_BY_NAME_PARAMETERS = scalaSettings.SHOW_ARGUMENTS_TO_BY_NAME_PARAMETERS;
  private boolean INCLUDE_BLOCK_EXPRESSIONS = scalaSettings.INCLUDE_BLOCK_EXPRESSIONS;
  private boolean INCLUDE_LITERALS = scalaSettings.INCLUDE_LITERALS;

  private boolean IGNORE_PERFORMANCE_TO_FIND_ALL_CLASS_NAMES = scalaSettings.IGNORE_PERFORMANCE_TO_FIND_ALL_CLASS_NAMES;
  private boolean TREAT_DOC_COMMENT_AS_BLOCK_COMMENT = scalaSettings.TREAT_DOC_COMMENT_AS_BLOCK_COMMENT;
  private boolean DISABLE_LANGUAGE_INJECTION = scalaSettings.DISABLE_LANGUAGE_INJECTION;

  private boolean SCALA_CLASSES_PRIORITY = scalaSettings.SCALA_CLASSES_PRIORITY;

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

  public boolean isAddUnambigiousImportsOnTheFly() {
    return ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
  }

  public void setAddUnambigiousImportsOnTheFly(boolean value) {
    ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = value;
  }

  public int getClassCountToUseImportOnDemand() {
    return CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND;
  }

  public void setClassCountToUseImportOnDemand(int value) {
    CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = value;
  }

  public boolean isAddImportMostCloseToReference() {
    return ADD_IMPORT_MOST_CLOSE_TO_REFERENCE;
  }

  public void setAddImportMostCloseToReference(boolean value) {
    ADD_IMPORT_MOST_CLOSE_TO_REFERENCE = value;
  }

  public boolean isAddFullQualifiedImports() {
    return ADD_FULL_QUALIFIED_IMPORTS;
  }

  public void setAddFullQualifiedImports(boolean value) {
    ADD_FULL_QUALIFIED_IMPORTS = value;
  }

  public boolean isImportMembersUsingUnderScore() {
    return IMPORTS_MEMBERS_USING_UNDERSCORE;
  }

  public void setImportMembersUsingUnderScore(boolean value) {
    IMPORTS_MEMBERS_USING_UNDERSCORE = value;
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

  public boolean isDisableLangInjection() {
    return DISABLE_LANGUAGE_INJECTION;
  }

  public void setDisableLangInjection(boolean value) {
    DISABLE_LANGUAGE_INJECTION = value;
  }

  public boolean isScalaPriority() {
    return SCALA_CLASSES_PRIORITY;
  }

  public void setScalaPriority(boolean value) {
    SCALA_CLASSES_PRIORITY = value;
  }

}
