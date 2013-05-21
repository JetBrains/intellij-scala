package org.jetbrains.plugins.scala.settings;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private int IMPLICIT_PARAMETERS_SEARCH_DEPTH = 5;

  private boolean IMPORT_SHORTEST_PATH_FOR_AMBIGUOUS_REFERENCES = true;
  private int CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 5;
  private int SHIFT = 80;
  private int OUTPUT_LIMIT = 35;
  private boolean ADD_IMPORT_MOST_CLOSE_TO_REFERENCE = false;
  private boolean ADD_FULL_QUALIFIED_IMPORTS = true;
  private boolean SORT_IMPORTS = false;
  private boolean IMPORTS_MEMBERS_USING_UNDERSCORE = true;

  private boolean SEARCH_ALL_SYMBOLS = false;
  private boolean ENABLE_JAVA_TO_SCALA_CONVERSION = true;
  private boolean DONT_SHOW_CONVERSION_DIALOG = false;

  private boolean SHOW_IMPLICIT_CONVERSIONS = true;
  private boolean SHOW_ARGUMENTS_TO_BY_NAME_PARAMETERS = false;
  private boolean INCLUDE_BLOCK_EXPRESSIONS = false;
  private boolean INCLUDE_LITERALS = false;

  private boolean IGNORE_PERFORMANCE_TO_FIND_ALL_CLASS_NAMES = false;
  private boolean TREAT_DOC_COMMENT_AS_BLOCK_COMMENT = false;
  private boolean DISABLE_LANGUAGE_INJECTION = false;
  private boolean DISABLE_I18N = false;

  private boolean SCALA_CLASSES_PRIORITY = true;

  private Map<String, String> INTERPOLATED_INJECTION_MAPPING = new HashMap<String, String>();

  {
    INTERPOLATED_INJECTION_MAPPING.put("sql", "SQL");
    INTERPOLATED_INJECTION_MAPPING.put("sqlu", "SQL");
    INTERPOLATED_INJECTION_MAPPING.put("xml", "XML");
  }
  
  private String[] IMPORTS_WITH_PREFIX = {
      "scala.collection.mutable._",
      "java.util.AbstractCollection",
      "java.util.AbstractList",
      "java.util.AbstractMap",
      "java.util.AbstractQueue",
      "java.util.AbstractSequentialList",
      "java.util.AbstractSet",
      "java.util.ArrayDeque",
      "java.util.ArrayList",
      "java.util.Arrays",
      "java.util.BitSet",
      "java.util.Collection",
      "java.util.Deque",
      "java.util.Enumeration",
      "java.util.EnumMap",
      "java.util.EnumSet",
      "java.util.HashMap",
      "java.util.HashSet",
      "java.util.Hahtable",
      "java.util.IdentityHashMap",
      "java.util.Iterator",
      "java.util.LinkedHashMap",
      "java.util.LikedHashSet",
      "java.util.LinkedList",
      "java.util.List",
      "java.util.ListIterator",
      "java.util.Map",
      "java.util.NavigableMap",
      "java.util.NavigableSet",
      "java.util.Queue",
      "java.util.Set",
      "java.util.SortedMap",
      "java.util.SortedSet",
      "java.util.Stack",
      "java.util.SubList",
      "java.util.TreeMap",
      "java.util.TreeSet",
      "java.util.Vector",
      "java.util.WeakHashMap",
      "exclude:scala.collection.mutable.ArrayBuffer",
      "exclude:scala.collection.mutable.ListBuffer",
      "org.scalatest.fixture._",
      "org.scalatest.path._"
  };

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

  public int getClassCountToUseImportOnDemand() {
    return CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND;
  }

  public void setClassCountToUseImportOnDemand(int value) {
    CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = value;
  }

  public int getShift() {
    return  SHIFT;
  }

  public void setShift(int value) {
    SHIFT = value;
  }

  public int getOutputLimit() {
    return  OUTPUT_LIMIT;
  }

  public void setOutputLimit(int value) {
    OUTPUT_LIMIT = value;
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

  public boolean isSortImports() {
    return SORT_IMPORTS;
  }

  public void setSortImports(boolean value) {
    SORT_IMPORTS = value;
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

  public boolean isDisableI18N() {
    return DISABLE_I18N;
  }

  public void setDisableI18N(boolean value) {
    DISABLE_I18N = value;
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

  public int getCollectionTypeHighlightingLevel() {
    return COLLECTION_TYPE_HIGHLIGHTING_LEVEL;
  }

  public void setCollectionTypeHighlightingLevel(int level) {
    this.COLLECTION_TYPE_HIGHLIGHTING_LEVEL = level;
  }

  public boolean isImportShortestPathForAmbiguousReferences() {
    return IMPORT_SHORTEST_PATH_FOR_AMBIGUOUS_REFERENCES;
  }

  public void setImportShortestPathForAmbiguousReferences(boolean importShortestPathForAmbiguousReferences) {
    this.IMPORT_SHORTEST_PATH_FOR_AMBIGUOUS_REFERENCES = importShortestPathForAmbiguousReferences;
  }

  public String[] getImportsWithPrefix() {
    return IMPORTS_WITH_PREFIX;
  }

  public void setImportsWithPrefix(String[] importsWithPrefix) {
    this.IMPORTS_WITH_PREFIX = importsWithPrefix;
  }
  
  public Map<String, String> getIntInjectionMapping() {
    return INTERPOLATED_INJECTION_MAPPING;
  }
  
  public void setIntInjectionMapping(Map<String, String> intInjectionMapping) {
    INTERPOLATED_INJECTION_MAPPING = intInjectionMapping;
  }

  public static String EXCLUDE_PREFIX = "exclude:";

  public boolean hasImportWithPrefix(@Nullable String qualName) {
    if (qualName != null && qualName.contains(".")) {
      String[] importsWithPrefix = getImportsWithPrefix();
      return nameFitToPatterns(qualName, importsWithPrefix);
    } else return false;
  }

  /**
   * Checks whether qualified class name fit to the list of patterns.
   * Expamples of patterns:
   * "java.util.ArrayList"                              java.util.ArrayList added
   * "scala.collection.mutable._"                       all classes from package scala.collection.mutable added
   * "exclude:scala.Option"                             scala.Option excluded
   * "exclude:scala.collection.immutable._"             all classes from package scala.collection.immutable excluded
   * */
  public static boolean nameFitToPatterns(String qualName, String[] patterns) {
    boolean res = false;
    for (String pattern : patterns) {
      if (pattern.startsWith(EXCLUDE_PREFIX)) {
        String s = pattern.substring(EXCLUDE_PREFIX.length());
        if (s.endsWith("._")) {
          if (s.substring(0, s.lastIndexOf('.')).equals(qualName.substring(0, qualName.lastIndexOf('.')))) {
            return false;
          }
        } else if (s.equals(qualName)) return false;
      } else {
        if (pattern.endsWith("._")) {
          if (pattern.substring(0, pattern.lastIndexOf('.')).equals(qualName.substring(0, qualName.lastIndexOf('.')))) {
            res = true;
          }
        } else if (pattern.equals(qualName)) res = true;
      }
    }
    return res;
  }
}
