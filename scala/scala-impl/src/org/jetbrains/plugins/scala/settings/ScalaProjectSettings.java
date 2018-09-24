package org.jetbrains.plugins.scala.settings;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.components.libextensions.LibraryExtensionsManager$;
import org.jetbrains.plugins.scala.statistics.FeatureKey;
import org.jetbrains.plugins.scala.statistics.Stats;

import java.io.File;
import java.util.*;

/**
 * @author Ksenia.Sautina
 * @since 4/25/12
 */

@State(
    name = "ScalaProjectSettings",
    storages = {
        @Storage(StoragePathMacros.WORKSPACE_FILE),
        @Storage("scala_settings.xml")
    }
)
public class ScalaProjectSettings  implements PersistentStateComponent<ScalaProjectSettings>, ExportableComponent {
  private int IMPLICIT_PARAMETERS_SEARCH_DEPTH = -1;

  private String[] BASE_PACKAGES = new String[0];
  private String SCALATEST_DEFAULT_SUPERCLASS = "org.scalatest.FunSuite";

  private boolean SEARCH_ALL_SYMBOLS = false;

  private boolean ENABLE_JAVA_TO_SCALA_CONVERSION = true;
  private boolean ADD_OVERRIDE_TO_IMPLEMENT_IN_CONVERTER = true;
  private boolean DONT_SHOW_CONVERSION_DIALOG = false;
  private boolean SHOW_IMPLICIT_CONVERSIONS = false;
  private boolean SHOW_NOT_FOUND_IMPLICIT_ARGUMENTS = true;

  private boolean SHOW_ARGUMENTS_TO_BY_NAME_PARAMETERS = false;
  private boolean INCLUDE_BLOCK_EXPRESSIONS = false;
  private boolean INCLUDE_LITERALS = false;
  private boolean IGNORE_PERFORMANCE_TO_FIND_ALL_CLASS_NAMES = false;

  private boolean CUSTOM_SCALATEST_SYNTAX_HIGHLIGHTING = false;

  private boolean TREAT_DOC_COMMENT_AS_BLOCK_COMMENT = false;
  private boolean DISABLE_LANGUAGE_INJECTION = true;
  private boolean DISABLE_I18N = false;
  private boolean DONT_CACHE_COMPOUND_TYPES = false;
  private boolean AOT_COMPLETION = true;
  private boolean PROJECT_VIEW_HIGHLIGHTING = false;
  private boolean SCALA_CLASSES_PRIORITY = true;
  private boolean GENERATE_TOSTRING_WITH_FIELD_NAMES = false;

  public enum TrailingCommasMode {Enabled, Disabled, Auto}
  private TrailingCommasMode TRAILING_COMMAS_ENABLED = TrailingCommasMode.Auto;

  //SCALA.META
  public enum ScalaMetaMode {Enabled, Disabled, Manual}
  private ScalaMetaMode scalaMetaMode = ScalaMetaMode.Enabled;
  private boolean metaTrimMethodBodies = true;

  //WORKSHEET
  private int OUTPUT_LIMIT = 35;
  private boolean IN_PROCESS_MODE = true;
  private boolean USE_ECLIPSE_COMPATIBILITY = false;
  private boolean TREAT_SCRATCH_AS_WORKSHEET = true;
  private boolean IS_WORKSHEET_FOLD_COLLAPSED_BY_DEFAULT = true;
  private int AUTORUN_DELAY = 1400;
  public enum ScFileMode {Worksheet, Ammonite, Auto}
  private ScFileMode SC_FILE_MODE = ScFileMode.Worksheet;

  //BREADCRUMBS
  private boolean BREADCRUMBS_CLASS_ENABLED = true;
  private boolean BREADCRUMBS_FUNCTION_ENABLED = true;
  private boolean BREADCRUMBS_LAMBDA_ENABLED = true;
  private boolean BREADCRUMBS_MATCH_ENABLED = false;
  private boolean BREADCRUMBS_VAL_DEF_ENABLED = false;
  private boolean BREADCRUMBS_IF_DO_WHILE_ENABLED = false;

  //MIGRATORS AND BUNDLED INSPECTIONS
  private boolean BUNDLED_MIGRATORS_SEARCH_ENABLED = false;
  private boolean BUNDLED_INSPECTIONS_SEARCH_ENABLED = false;
  private Set<String> BUNDLED_INSPECTION_IDS_DISABLED = new HashSet<String>();
  private Map<String, ArrayList<String>> BUNDLED_LIB_JAR_PATHS_TO_INSPECTIONS = new HashMap<String, ArrayList<String>>();

  // LIBRARY EXTENSIONS
  private boolean ENABLE_LIBRARY_EXTENSIONS = false;
  private boolean LEXT_SHOW_ALL_PROJECTS = false;
  private Set<Integer> DISABLED_EXTENSIONS = new HashSet<>();
  private List<String> LEXT_SEARCH_PATTERNS = new ArrayList<>();

  {
    Collections.addAll(LEXT_SEARCH_PATTERNS, LibraryExtensionsManager$.MODULE$.DEFAULT_PATTERNS());
  }

  //INDEXING
  private boolean ENABLE_LOCAL_DEPENDENCY_INDEXING = true;


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

  public boolean isAddOverrideToImplementInConverter() {
    return ADD_OVERRIDE_TO_IMPLEMENT_IN_CONVERTER;
  }

  public void setAddOverrideToImplementInConverter(boolean value) {
    ADD_OVERRIDE_TO_IMPLEMENT_IN_CONVERTER = value;
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

  public void setShowNotFoundImplicitArguments(boolean value) {
    SHOW_NOT_FOUND_IMPLICIT_ARGUMENTS = value;
  }

  public boolean isShowNotFoundImplicitArguments() {
    return SHOW_NOT_FOUND_IMPLICIT_ARGUMENTS;
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

  public boolean isCustomScalatestSyntaxHighlighting() {
    return CUSTOM_SCALATEST_SYNTAX_HIGHLIGHTING;
  }

  public void setCustomScalatestSyntaxHighlighting(boolean value) {
    CUSTOM_SCALATEST_SYNTAX_HIGHLIGHTING = value;
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

  public boolean isProjectViewHighlighting() {
    return PROJECT_VIEW_HIGHLIGHTING;
  }

  public void setProjectViewHighlighting(boolean value) {
    PROJECT_VIEW_HIGHLIGHTING = value;
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

  public List<String> getBasePackages() {
    return new ArrayList<String>(Arrays.asList(BASE_PACKAGES));
  }

  public String getScalaTestDefaultSuperClass() {
    return SCALATEST_DEFAULT_SUPERCLASS;
  }

  public void setScalaTestDefaultSuperClass(String superClassName) {
    SCALATEST_DEFAULT_SUPERCLASS = superClassName;
  }

  public void setBasePackages(List<String> packages) {
    BASE_PACKAGES = packages.toArray(new String[packages.size()]);
  }

  public void setIntInjectionMapping(Map<String, String> intInjectionMapping) {
    INTERPOLATED_INJECTION_MAPPING = intInjectionMapping;
  }

  public boolean isWorksheetFoldCollapsedByDefault() {
    return IS_WORKSHEET_FOLD_COLLAPSED_BY_DEFAULT;
  }
  
  public void setWorksheetFoldCollapsedByDefault(boolean isCollapsed) {
    IS_WORKSHEET_FOLD_COLLAPSED_BY_DEFAULT = isCollapsed;
  }
  
  public boolean isInProcessMode() {
    return IN_PROCESS_MODE;
  }

  public void setInProcessMode(boolean inProcess) {
    this.IN_PROCESS_MODE = inProcess;
  }

  public boolean isUseEclipseCompatibility() {
    return USE_ECLIPSE_COMPATIBILITY;
  }

  public void setUseEclipseCompatibility(boolean USE_ECLIPSE_COMPATIBILITY) {
    this.USE_ECLIPSE_COMPATIBILITY = USE_ECLIPSE_COMPATIBILITY;
  }

  public int getAutoRunDelay() {
    return AUTORUN_DELAY;
  }

  public void setAutoRunDelay(int delay) {
    AUTORUN_DELAY = delay;
  }

  public boolean isTreatScratchFilesAsWorksheet() {
    return TREAT_SCRATCH_AS_WORKSHEET;
  }

  public void setTreatScratchFilesAsWorksheet(boolean b) {
    TREAT_SCRATCH_AS_WORKSHEET = b;
  }

  public ScFileMode getScFileMode() {
    return SC_FILE_MODE;
  }
  
  public void setScFileMode(ScFileMode mode) {
    Stats.trigger(FeatureKey.scFileModeSet(mode.name()));
    SC_FILE_MODE = mode;
  }
  
  public void setBreadcrumbsClassEnabled(boolean enabled) {
    BREADCRUMBS_CLASS_ENABLED = enabled;
  }

  public void setBreadcrumbsFunctionEnabled(boolean enabled) {
    BREADCRUMBS_FUNCTION_ENABLED = enabled;
  }

  public void setBreadcrumbsLambdaEnabled(boolean enabled) {
    BREADCRUMBS_LAMBDA_ENABLED = enabled;
  }

  public void setBreadcrumbsMatchEnabled(boolean enabled) {
    BREADCRUMBS_MATCH_ENABLED = enabled;
  }

  public void setBreadcrumbsValDefEnabled(boolean enabled) {
    BREADCRUMBS_VAL_DEF_ENABLED = enabled;
  }

  public void setBreadcrumbsIfDoWhileEnabled(boolean enabled) {
    BREADCRUMBS_IF_DO_WHILE_ENABLED = enabled;
  }

  public boolean isBreadcrumbsClassEnabled() {
    return BREADCRUMBS_CLASS_ENABLED;
  }

  public boolean isBreadcrumbsFunctionEnabled() {
    return BREADCRUMBS_FUNCTION_ENABLED;
  }

  public boolean isBreadcrumbsLambdaEnabled() {
    return BREADCRUMBS_LAMBDA_ENABLED;
  }

  public boolean isBreadcrumbsMatchEnabled() {
    return BREADCRUMBS_MATCH_ENABLED;
  }

  public boolean isBreadcrumbsValDefEnabled() {
    return BREADCRUMBS_VAL_DEF_ENABLED;
  }

  public boolean isBreadcrumbsIfDoWhileEnabled() {
    return BREADCRUMBS_IF_DO_WHILE_ENABLED;
  }

  public boolean isBundledMigratorsSearchEnabled() {
    return BUNDLED_MIGRATORS_SEARCH_ENABLED;
  }

  public boolean isBundledInspectionsSearchEnabled() {
    return BUNDLED_INSPECTIONS_SEARCH_ENABLED;
  }

  public Set<String> getBundledInspectionIdsDisabled() {
    return BUNDLED_INSPECTION_IDS_DISABLED;
  }

  public Map<String, ArrayList<String>> getBundledLibJarsPathsToInspections() {
    return BUNDLED_LIB_JAR_PATHS_TO_INSPECTIONS;
  }

  public void setBundledMigratorsSearchEnabled(boolean enabled) {
    BUNDLED_MIGRATORS_SEARCH_ENABLED = enabled;
  }

  public void setBundledInspectionsSearchEnabled(boolean enabled) {
    BUNDLED_INSPECTIONS_SEARCH_ENABLED = enabled;
  }

  public void setBundledInspectionsIdsDisabled(Set<String> disabled) {
    BUNDLED_INSPECTION_IDS_DISABLED = disabled;
  }

  public void setBundledLibJarsPathsToInspections(Map<String, ? extends List<String>> pathToInspections) {
    BUNDLED_LIB_JAR_PATHS_TO_INSPECTIONS = new HashMap<String, ArrayList<String>>();
    for (Map.Entry<String, ? extends List<String>> entry : pathToInspections.entrySet()) {
      BUNDLED_LIB_JAR_PATHS_TO_INSPECTIONS.put(entry.getKey(), new ArrayList<String>(entry.getValue()));
    }
  }

  public boolean isGenerateToStringWithFieldNames() { return GENERATE_TOSTRING_WITH_FIELD_NAMES; }

  public void setGenerateToStringWithFieldNames(boolean generateToStringWithFieldNames) {
    this.GENERATE_TOSTRING_WITH_FIELD_NAMES = generateToStringWithFieldNames;
  }
  
  public TrailingCommasMode getTrailingCommasMode() {
    return TRAILING_COMMAS_ENABLED;
  }

  public void setTrailingCommasMode(TrailingCommasMode mode) {
    TRAILING_COMMAS_ENABLED = mode;
  }

  public boolean isEnableLibraryExtensions() {
    return ENABLE_LIBRARY_EXTENSIONS;
  }

  public void setEnableLibraryExtensions(boolean ENABLE_LIBRARY_EXTENSIONS) {
    this.ENABLE_LIBRARY_EXTENSIONS = ENABLE_LIBRARY_EXTENSIONS;
  }

  public boolean isLextShowAllProjects() {
    return LEXT_SHOW_ALL_PROJECTS;
  }

  public void setLextShowAllProjects(boolean LEXT_SHOW_ALL_PROJECTS) {
    this.LEXT_SHOW_ALL_PROJECTS = LEXT_SHOW_ALL_PROJECTS;
  }

  public Set<Integer> getDisabledExtensions() {
    return DISABLED_EXTENSIONS;
  }

  public void setDisabledExtensions(Set<Integer> DISABLED_EXTENSIONS) {
    this.DISABLED_EXTENSIONS = DISABLED_EXTENSIONS;
  }

  public List<String> getLextSearchPatterns() {
      return LEXT_SEARCH_PATTERNS;
  }

  public void setLextSearchPatterns(List<String> LEXT_SEARCH_PATTERNS) {
      this.LEXT_SEARCH_PATTERNS = LEXT_SEARCH_PATTERNS;
  }

    public ScalaMetaMode getScalaMetaMode() {
    return scalaMetaMode;
  }

  public void setScalaMetaMode(ScalaMetaMode scalaMetaMode) {
    this.scalaMetaMode = scalaMetaMode;
  }

  public boolean isMetaTrimMethodBodies() {
    return metaTrimMethodBodies;
  }

  public void setMetaTrimMethodBodies(boolean metaTrimMethodBodies) {
    this.metaTrimMethodBodies = metaTrimMethodBodies;
  }

  public boolean isEnableLocalDependencyIndexing() {
    return ENABLE_LOCAL_DEPENDENCY_INDEXING;
  }

  public void setEnableLocalDependencyIndexing(boolean ENABLE_LOCAL_DEPENDENCY_INDEXING) {
    this.ENABLE_LOCAL_DEPENDENCY_INDEXING = ENABLE_LOCAL_DEPENDENCY_INDEXING;
  }

}
