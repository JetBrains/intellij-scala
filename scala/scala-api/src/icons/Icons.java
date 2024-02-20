package org.jetbrains.plugins.scala.icons;

import javax.swing.*;

import static com.intellij.openapi.util.IconLoader.getIcon;

/**
 * NOTE: this class contains references to the icons in the "Old UI".
 * When the "New UI" is enabled, an automatic mapping is done between old icons and new icons.
 * The mappings are defined in ScalaIconMappings.json
 *
 * @see com.intellij.icons.AllIcons
 */
public interface Icons {

    Icon COMPILE_SERVER = getIcon("/org/jetbrains/plugins/scala/images/compileServer.svg", Icons.class);
    Icon COMPILATION_CHARTS = getIcon("/org/jetbrains/plugins/scala/images/compilationCharts.svg", Icons.class);

    Icon SCALA_FILE = getIcon("/org/jetbrains/plugins/scala/images/fileScala.svg", Icons.class);

    Icon ADD_CLAUSE = getIcon("/org/jetbrains/plugins/scala/images/addClause.svg", Icons.class);
    Icon REMOVE_CLAUSE = getIcon("/org/jetbrains/plugins/scala/images/removeClause.svg", Icons.class);

    //SDK configuration
    Icon SCALA_SDK = getIcon("/org/jetbrains/plugins/scala/images/scalaSdk.svg", Icons.class);

    //Toplevel nodes
    Icon CLASS = getIcon("/org/jetbrains/plugins/scala/images/classScala.svg", Icons.class);
    Icon OBJECT = getIcon("/org/jetbrains/plugins/scala/images/objectScala.svg", Icons.class);
    Icon CASE_CLASS = CLASS;
    Icon CASE_OBJECT = OBJECT;
    Icon ABSTRACT_CLASS = getIcon("/org/jetbrains/plugins/scala/images/abstractClassScala.svg", Icons.class);
    Icon TRAIT = getIcon("/org/jetbrains/plugins/scala/images/traitScala.svg", Icons.class);
    Icon PACKAGE_OBJECT = getIcon("/org/jetbrains/plugins/scala/images/packageObject.svg", Icons.class);
    Icon CLASS_AND_OBJECT = getIcon("/org/jetbrains/plugins/scala/images/classObjectScala.svg", Icons.class);
    Icon ABSTRACT_CLASS_AND_OBJECT = getIcon("/org/jetbrains/plugins/scala/images/abstractClassObjectScala.svg", Icons.class);
    Icon TRAIT_AND_OBJECT = getIcon("/org/jetbrains/plugins/scala/images/traitObjectScala.svg", Icons.class);
    Icon ENUM_AND_OBJECT = getIcon("/org/jetbrains/plugins/scala/images/enumObjectScala.svg", Icons.class);

    Icon ENUM = getIcon("/org/jetbrains/plugins/scala/images/enumScala.svg", Icons.class);

    //Companion gutter icons
    Icon CLASS_COMPANION = getIcon("/org/jetbrains/plugins/scala/images/classCompanion.svg", Icons.class);
    Icon CLASS_COMPANION_SWAPPED = getIcon("/org/jetbrains/plugins/scala/images/classCompanionSwapped.svg", Icons.class);
    Icon TRAIT_COMPANION = getIcon("/org/jetbrains/plugins/scala/images/traitCompanion.svg", Icons.class);
    Icon TRAIT_COMPANION_SWAPPED = getIcon("/org/jetbrains/plugins/scala/images/traitCompanionSwapped.svg", Icons.class);
    Icon OBJECT_COMPANION = getIcon("/org/jetbrains/plugins/scala/images/objectCompanion.svg", Icons.class);
    Icon OBJECT_COMPANION_SWAPPED = getIcon("/org/jetbrains/plugins/scala/images/objectCompanionSwapped.svg", Icons.class);

    //Internal nodes
    Icon FIELD_VAR = getIcon("/org/jetbrains/plugins/scala/images/fieldVariable.svg", Icons.class);
    Icon ABSTRACT_FIELD_VAR = getIcon("/org/jetbrains/plugins/scala/images/abstractFieldVariable.svg", Icons.class);
    Icon FIELD_VAL = getIcon("/org/jetbrains/plugins/scala/images/fieldValue.svg", Icons.class);
    Icon ABSTRACT_FIELD_VAL = getIcon("/org/jetbrains/plugins/scala/images/abstractFieldValue.svg", Icons.class);
    Icon TYPE_ALIAS = getIcon("/org/jetbrains/plugins/scala/images/typeAlias.svg", Icons.class);
    Icon ABSTRACT_TYPE_ALIAS = getIcon("/org/jetbrains/plugins/scala/images/abstractTypeAlias.svg", Icons.class);
    Icon FUNCTION = getIcon("/org/jetbrains/plugins/scala/images/function.svg", Icons.class);
    Icon EXTENSION = FUNCTION; // TODO: need dedicated icon or not?
    Icon VAR = getIcon("/org/jetbrains/plugins/scala/images/variable.svg", Icons.class);
    Icon VAL = getIcon("/org/jetbrains/plugins/scala/images/value.svg", Icons.class);
    Icon PARAMETER = getIcon("/org/jetbrains/plugins/scala/images/parameter.svg", Icons.class);
    Icon PATTERN_VAL = getIcon("/org/jetbrains/plugins/scala/images/patternValue.svg", Icons.class);
    Icon LAMBDA = getIcon("/org/jetbrains/plugins/scala/images/lambda.svg", Icons.class);
    Icon RECURSION = getIcon("/org/jetbrains/plugins/scala/images/recursion.svg", Icons.class);
    Icon TAIL_RECURSION = getIcon("/org/jetbrains/plugins/scala/images/tailRecursion.svg", Icons.class);

    //Testing support
    Icon SCALA_TEST = getIcon("/org/jetbrains/plugins/scala/images/scalaTestIcon.svg", Icons.class);
    Icon SCALA_TEST_NODE = getIcon("/org/jetbrains/plugins/scala/images/test.svg", Icons.class);

    //Console
    Icon SCALA_CONSOLE = getIcon("/org/jetbrains/plugins/scala/images/scalaConsole.svg", Icons.class);

    // Highlighting (status bar)
    Icon TYPED = getIcon("/org/jetbrains/plugins/scala/images/typed.svg", Icons.class);
    Icon UNTYPED = getIcon("/org/jetbrains/plugins/scala/images/untyped.svg", Icons.class);
}
