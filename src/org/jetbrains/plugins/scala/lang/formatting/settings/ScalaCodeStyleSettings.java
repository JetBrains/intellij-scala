package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Alexander Podkhalyuzin
 * Date: 28.07.2008
 */
public class ScalaCodeStyleSettings extends CustomCodeStyleSettings {
  public ScalaCodeStyleSettings() {
    super("ScalaCodeStyleSettings", null);
  }

  public boolean WRAP_BEFORE_WITH_KEYWORD = false;
  public int METHOD_BRACE_FORCE = 0;
  public int FINALLY_BRACE_FORCE = 0;
  public int TRY_BRACE_FORCE = 0;
  public int CLOSURE_BRACE_FORCE = 0;
  public int CASE_CLAUSE_BRACE_FORCE = 0;
  public boolean PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE = false;
  public boolean SPACE_INSIDE_CLOSURE_BRACES = true;
  public boolean PLACE_SELF_TYPE_ON_NEW_LINE = true;
  public boolean ALIGN_IF_ELSE = false;
  //indents
  public boolean NOT_CONTINUATION_INDENT_FOR_PARAMS = false;
  public boolean ALIGN_IN_COLUMNS_CASE_BRANCH = false;
  public boolean ALIGN_COMPOSITE_PATTERN = true;
  public boolean DO_NOT_ALIGN_BLOCK_EXPR_PARAMS = false;

  public boolean SPACE_AFTER_MODIFIERS_CONSTRUCTOR = false;

  public boolean SPACE_BEFORE_INFIX_METHOD_CALL_PARENTHESES = false;
  public boolean INSERT_WHITESPACES_IN_SIMPLE_ONE_LINE_METHOD = true;

  public boolean SPACE_BEFORE_TYPE_COLON = false;
  public boolean SPACE_AFTER_TYPE_COLON = true;

  //todo: add to spacing settings
  //spacing settings:
  public boolean SPACE_BEFORE_BRACE_METHOD_CALL = true;
  public boolean SPACE_BEFORE_MATCH_LBRACE = true;
  public boolean KEEP_ONE_LINE_LAMBDAS_IN_ARG_LIST = false;

  public boolean USE_SCALADOC2_FORMATTING = false;

  public boolean PRESERVE_SPACE_AFTER_METHOD_DECLARATION_NAME = false;
  public boolean SPACE_BEFORE_INFIX_LIKE_METHOD_PARENTHESES = false;

  public boolean SPACES_IN_ONE_LINE_BLOCKS = false;
  public boolean SPACES_IN_IMPORTS = false;
  public boolean SPACES_AROUND_AT_IN_PATTERNS = false;

  //xml formatting
  public boolean KEEP_XML_FORMATTING = false;

  //multiline strings support
  public int MULTILINE_STRING_SUPORT = MULTILINE_STRING_ALL;
  public char MARGIN_CHAR = '|';
  public boolean MULTI_LINE_QUOTES_ON_NEW_LINE = true;
  public boolean KEEP_MULTI_LINE_QUOTES = true;
  public int MULTI_LINE_STRING_MARGIN_INDENT = 2;
  public boolean PROCESS_MARGIN_ON_COPY_PASTE = true;

  public static final int MULTILINE_STRING_NONE = 0;
  public static final int MULTILINE_STRING_QUOTES_AND_INDENT = 1;
  public static final int MULTILINE_STRING_ALL = 2;

  //type annotations
  public int LOCAL_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal();
  public int PUBLIC_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Preferred.ordinal();
  public int PROTECTED_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Preferred.ordinal();
  public int PRIVATE_PROPERTY_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal();
  public int OVERRIDING_PROPERTY_TYPE_ANNOTATION = TypeAnnotationPolicy.Regular.ordinal();
  public int SIMPLE_PROPERTY_TYPE_ANNOTATION = TypeAnnotationPolicy.Optional.ordinal();

  public int LOCAL_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal();
  public int PUBLIC_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Preferred.ordinal();
  public int PROTECTED_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Preferred.ordinal();
  public int PRIVATE_METHOD_TYPE_ANNOTATION = TypeAnnotationRequirement.Optional.ordinal();
  public int OVERRIDING_METHOD_TYPE_ANNOTATION = TypeAnnotationPolicy.Regular.ordinal();
  public int SIMPLE_METHOD_TYPE_ANNOTATION = TypeAnnotationPolicy.Optional.ordinal();

  //other
  public boolean ENFORCE_FUNCTIONAL_SYNTAX_FOR_UNIT = true;
  public boolean REPLACE_CASE_ARROW_WITH_UNICODE_CHAR = false;
  public boolean REPLACE_MAP_ARROW_WITH_UNICODE_CHAR = false;
  public boolean REPLACE_FOR_GENERATOR_ARROW_WITH_UNICODE_CHAR = false;
  public boolean REPLACE_LAMBDA_WITH_GREEK_LETTER = false;


  @Override
  public void readExternal(Element parentElement) throws InvalidDataException {
    Element scalaCodeStyleSettings = parentElement.getChild("ScalaCodeStyleSettings");
    if (scalaCodeStyleSettings != null) {
      XmlSerializer.deserializeInto(this, scalaCodeStyleSettings);
    }
  }

  @Override
  public void writeExternal(Element parentElement, @NotNull CustomCodeStyleSettings parentSettings) throws WriteExternalException {
    Element scalaCodeStyleSettings = new Element("ScalaCodeStyleSettings");
    parentElement.addContent(scalaCodeStyleSettings);
    XmlSerializer.serializeInto(this, scalaCodeStyleSettings, new SkipDefaultValuesSerializationFilters());
    if (scalaCodeStyleSettings.getChildren().isEmpty()) {
      parentElement.removeChild("ScalaCodeStyleSettings");
    }
  }

  //import
  private boolean IMPORT_SHORTEST_PATH_FOR_AMBIGUOUS_REFERENCES = true;
  private int CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 5;
  private boolean ADD_IMPORT_MOST_CLOSE_TO_REFERENCE = false;
  private boolean ADD_FULL_QUALIFIED_IMPORTS = true;
  private boolean DO_NOT_CHANGE_LOCAL_IMPORTS_ON_OPTIMIZE = true;
  private boolean SORT_IMPORTS = true;
  private boolean IMPORTS_MEMBERS_USING_UNDERSCORE = true;
  private boolean COLLECT_IMPORTS_TOGETHER = true;

  private String[] IMPORTS_WITH_PREFIX = new String[] {
      "exclude:scala.collection.mutable.ArrayBuffer",
      "exclude:scala.collection.mutable.ListBuffer",
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
      "java.util.EnumMap",
      "java.util.EnumSet",
      "java.util.Enumeration",
      "java.util.HashMap",
      "java.util.HashSet",
      "java.util.Hashtable",
      "java.util.IdentityHashMap",
      "java.util.Iterator",
      "java.util.LinkedHashMap",
      "java.util.LinkedHashSet",
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
      "org.scalatest.fixture._",
      "org.scalatest.path._",
      "scala.collection.mutable._",
      "scala.reflect.macros.blackbox.Context",
      "scala.reflect.macros.whitebox.Context"
  };

  private String[] IMPORT_LAYOUT = new String[] {
      "java",
      BLANK_LINE,
      ALL_OTHER_IMPORTS,
      BLANK_LINE,
      "scala"
  };

  public ScalaCodeStyleSettings(CodeStyleSettings container) {
    super("ScalaCodeStyleSettings", container);
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

  public boolean isSortImports() {
    return SORT_IMPORTS;
  }

  public void setSortImports(boolean value) {
    SORT_IMPORTS = value;
  }

  public boolean isCollectImports() {
    return COLLECT_IMPORTS_TOGETHER;
  }

  public void setCollectImports(boolean value) {
    COLLECT_IMPORTS_TOGETHER = value;
  }

  public boolean isImportMembersUsingUnderScore() {
    return IMPORTS_MEMBERS_USING_UNDERSCORE;
  }

  public void setImportMembersUsingUnderScore(boolean value) {
    IMPORTS_MEMBERS_USING_UNDERSCORE = value;
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

  public boolean hasImportWithPrefix(@Nullable String qualName) {
    if (qualName != null && qualName.contains(".")) {
      String[] importsWithPrefix = getImportsWithPrefix();
      return nameFitToPatterns(qualName, importsWithPrefix);
    } else return false;
  }

  public String[] getImportLayout() {
    return IMPORT_LAYOUT;
  }

  public void setImportLayout(String[] importLayout) {
    this.IMPORT_LAYOUT = importLayout;
  }

  private static boolean fitToUnderscorePattern(String pattern, String qualName) {
    return pattern.endsWith("._") && qualName.contains(".") && qualName.startsWith(pattern.substring(0, pattern.lastIndexOf('.'))) &&
            !qualName.equals(pattern.substring(0, pattern.lastIndexOf('.')));
  }

  public static String EXCLUDE_PREFIX = "exclude:";

  public static String BLANK_LINE = "_______ blank line _______";
  public static String ALL_OTHER_IMPORTS = "all other imports";

  public static ScalaCodeStyleSettings getInstance(Project project) {
    return CodeStyleSettingsManager.getSettings(project).getCustomSettings(ScalaCodeStyleSettings.class);
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
      if (pattern.startsWith(ScalaCodeStyleSettings.EXCLUDE_PREFIX)) {
        String s = pattern.substring(ScalaCodeStyleSettings.EXCLUDE_PREFIX.length());
        if (fitToUnderscorePattern(s, qualName) || s.equals(qualName))
          return false;
      }
      else {
        if (fitToUnderscorePattern(pattern, qualName) || pattern.equals(qualName))
          res = true;
      }
    }
    return res;
  }

  public boolean isDoNotChangeLocalImportsOnOptimize() {
    return DO_NOT_CHANGE_LOCAL_IMPORTS_ON_OPTIMIZE;
  }

  public void setDoNotChangeLocalImportsOnOptimize(boolean value) {
    this.DO_NOT_CHANGE_LOCAL_IMPORTS_ON_OPTIMIZE = value;
  }
}
