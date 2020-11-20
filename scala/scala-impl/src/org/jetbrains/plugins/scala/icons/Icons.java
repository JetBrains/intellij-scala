/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License", Icons.class);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.icons;

import javax.swing.*;

import static com.intellij.openapi.util.IconLoader.getIcon;

public interface Icons {

    Icon COMPILE_SERVER = getIcon("/org/jetbrains/plugins/scala/images/compileServer.svg", Icons.class);
    Icon COMPILATION_CHARTS = getIcon("/org/jetbrains/plugins/scala/images/compilation_charts.svg", Icons.class);

    Icon SCALA_SMALL_LOGO = getIcon("/org/jetbrains/plugins/scala/images/scala-small-logo.svg", Icons.class);
    Icon SCRIPT_FILE_LOGO = getIcon("/org/jetbrains/plugins/scala/images/scala_script_icon.svg", Icons.class);

    Icon ADD_CLAUSE = getIcon("/org/jetbrains/plugins/scala/images/AddClause.svg", Icons.class);
    Icon REMOVE_CLAUSE = getIcon("/org/jetbrains/plugins/scala/images/RemoveClause.svg", Icons.class);

    //SDK configuration
    Icon SCALA_SDK = getIcon("/org/jetbrains/plugins/scala/images/scala_sdk.svg", Icons.class);
    Icon NO_SCALA_SDK = getIcon("/org/jetbrains/plugins/scala/images/no_scala_sdk.svg", Icons.class);

    //Toplevel nodes
    Icon CLASS = getIcon("/org/jetbrains/plugins/scala/images/class_scala.svg", Icons.class);
    Icon OBJECT = getIcon("/org/jetbrains/plugins/scala/images/object_scala.svg", Icons.class);
    Icon CASE_CLASS = CLASS;
    Icon CASE_OBJECT = OBJECT;
    Icon ABSTRACT_CLASS = getIcon("/org/jetbrains/plugins/scala/images/abstract_class_scala.svg", Icons.class);
    Icon TRAIT = getIcon("/org/jetbrains/plugins/scala/images/trait_scala.svg", Icons.class);
    Icon PACKAGE_OBJECT = getIcon("/org/jetbrains/plugins/scala/images/package_object.svg", Icons.class);
    Icon CLASS_AND_OBJECT = getIcon("/org/jetbrains/plugins/scala/images/class_object_scala.svg", Icons.class);
    Icon ABSTRACT_CLASS_AND_OBJECT = getIcon("/org/jetbrains/plugins/scala/images/abstract_class_object_scala.svg", Icons.class);
    Icon TRAIT_AND_OBJECT = getIcon("/org/jetbrains/plugins/scala/images/trait_object_scala.svg", Icons.class);
    Icon PACKAGE_WITH_OBJECT = getIcon("/org/jetbrains/plugins/scala/images/packageWithObject.svg", Icons.class);
    Icon MULTIPLE_TYPE_DEFINITONS = getIcon("/org/jetbrains/plugins/scala/images/multipleTypeDefinitions.svg", Icons.class);

    //Companion gutter icons
    Icon CLASS_COMPANION = getIcon("/org/jetbrains/plugins/scala/images/class_companion.svg", Icons.class);
    Icon CLASS_COMPANION_SWAPPED = getIcon("/org/jetbrains/plugins/scala/images/class_companion_swapped.svg", Icons.class);
    Icon TRAIT_COMPANION = getIcon("/org/jetbrains/plugins/scala/images/trait_companion.svg", Icons.class);
    Icon TRAIT_COMPANION_SWAPPED = getIcon("/org/jetbrains/plugins/scala/images/trait_companion_swapped.svg", Icons.class);
    Icon OBECT_COMPANION = getIcon("/org/jetbrains/plugins/scala/images/object_companion.svg", Icons.class);
    Icon OBECT_COMPANION_SWAPPED = getIcon("/org/jetbrains/plugins/scala/images/object_companion_swapped.svg", Icons.class);

    //Internal nodes
    Icon FIELD_VAR = getIcon("/org/jetbrains/plugins/scala/images/field_variable.svg", Icons.class);
    Icon ABSTRACT_FIELD_VAR = getIcon("/org/jetbrains/plugins/scala/images/abstract_field_variable.svg", Icons.class);
    Icon FIELD_VAL = getIcon("/org/jetbrains/plugins/scala/images/field_value.svg", Icons.class);
    Icon ABSTRACT_FIELD_VAL = getIcon("/org/jetbrains/plugins/scala/images/abstract_field_value.svg", Icons.class);
    Icon TYPE_ALIAS = getIcon("/org/jetbrains/plugins/scala/images/type_alias.svg", Icons.class);
    Icon ABSTRACT_TYPE_ALIAS = getIcon("/org/jetbrains/plugins/scala/images/abstract_type_alias.svg", Icons.class);
    Icon FUNCTION = getIcon("/org/jetbrains/plugins/scala/images/function.svg", Icons.class);
    Icon VAR = getIcon("/org/jetbrains/plugins/scala/images/variable.svg", Icons.class);
    Icon VAL = getIcon("/org/jetbrains/plugins/scala/images/value.svg", Icons.class);
    Icon PARAMETER = getIcon("/org/jetbrains/plugins/scala/images/parameter.svg", Icons.class);
    Icon PATTERN_VAL = getIcon("/org/jetbrains/plugins/scala/images/pattern_value.svg", Icons.class);
    Icon LAMBDA = getIcon("/org/jetbrains/plugins/scala/images/lambda.svg", Icons.class);
    Icon RECURSION = getIcon("/org/jetbrains/plugins/scala/images/recursion.svg", Icons.class);
    Icon TAIL_RECURSION = getIcon("/org/jetbrains/plugins/scala/images/tail-recursion.svg", Icons.class);

    //Testing support
    Icon SCALA_TEST = getIcon("/org/jetbrains/plugins/scala/images/scala_test_icon.svg", Icons.class);
    Icon SCALA_TEST_NODE = getIcon("/org/jetbrains/plugins/scala/images/test.svg", Icons.class);

    //Console
    Icon SCALA_CONSOLE = getIcon("/org/jetbrains/plugins/scala/images/scala_console.svg", Icons.class);

    // Highlighting (status bar)
    Icon TYPED = getIcon("/org/jetbrains/plugins/scala/images/typed.svg", Icons.class);
    Icon UNTYPED = getIcon("/org/jetbrains/plugins/scala/images/untyped.svg", Icons.class);

    Icon LIGHTBEND_LOGO = getIcon("/org/jetbrains/plugins/scala/images/lightbend_logo.svg", Icons.class);

    // sbt
    Icon SBT = getIcon("/org/jetbrains/plugins/scala/images/sbt_icon.svg", Icons.class);
    // used from SBT.xml
    Icon SBT_TOOLWINDOW = getIcon("/org/jetbrains/plugins/scala/images/sbt_toolwin.svg", Icons.class);
    Icon SBT_FOLDER = getIcon("/org/jetbrains/plugins/scala/images/sbt_folder.svg", Icons.class);
    Icon SBT_SHELL = getIcon("/org/jetbrains/plugins/scala/images/sbt_shell.svg", Icons.class);
    // used from SBT.xml
    Icon SBT_SHELL_TOOLWINDOW = getIcon("/org/jetbrains/plugins/scala/images/sbt_shell_toolwin.svg", Icons.class);
    Icon SBT_LOAD_CHANGES = getIcon("/org/jetbrains/plugins/scala/images/sbtLoadChanges.svg", Icons.class);

}
