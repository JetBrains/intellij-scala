/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
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

    Icon COMPILE_SERVER = getIcon("/org/jetbrains/plugins/scala/images/compileServer.svg");

    Icon SCALA_SMALL_LOGO = getIcon("/org/jetbrains/plugins/scala/images/scala-small-logo.svg");
    Icon SCRIPT_FILE_LOGO = getIcon("/org/jetbrains/plugins/scala/images/scala_script_icon.svg");

    Icon ADD_CLAUSE = getIcon("/org/jetbrains/plugins/scala/images/AddClause.svg");
    Icon REMOVE_CLAUSE = getIcon("/org/jetbrains/plugins/scala/images/RemoveClause.svg");

    //SDK configuration
    Icon SCALA_SDK = getIcon("/org/jetbrains/plugins/scala/images/scala_sdk.svg");
    Icon NO_SCALA_SDK = getIcon("/org/jetbrains/plugins/scala/images/no_scala_sdk.svg");

    //Toplevel nodes
    Icon CLASS = getIcon("/org/jetbrains/plugins/scala/images/class_scala.svg");
    Icon OBJECT = getIcon("/org/jetbrains/plugins/scala/images/object_scala.svg");
    Icon CASE_CLASS = CLASS;
    Icon CASE_OBJECT = OBJECT;
    Icon ABSTRACT_CLASS = getIcon("/org/jetbrains/plugins/scala/images/abstract_class_scala.svg");
    Icon TRAIT = getIcon("/org/jetbrains/plugins/scala/images/trait_scala.svg");
    Icon PACKAGE_OBJECT = getIcon("/org/jetbrains/plugins/scala/images/package_object.svg");
    Icon CLASS_AND_OBJECT = getIcon("/org/jetbrains/plugins/scala/images/class_object_scala.svg");
    Icon ABSTRACT_CLASS_AND_OBJECT = getIcon("/org/jetbrains/plugins/scala/images/abstract_class_object_scala.svg");
    Icon TRAIT_AND_OBJECT = getIcon("/org/jetbrains/plugins/scala/images/trait_object_scala.svg");
    Icon PACKAGE_WITH_OBJECT = getIcon("/org/jetbrains/plugins/scala/images/packageWithObject.svg");
    Icon MULTIPLE_TYPE_DEFINITONS = getIcon("/org/jetbrains/plugins/scala/images/multipleTypeDefinitions.svg");

    //Companion gutter icons
    Icon CLASS_COMPANION = getIcon("/org/jetbrains/plugins/scala/images/class_companion.svg");
    Icon CLASS_COMPANION_SWAPPED = getIcon("/org/jetbrains/plugins/scala/images/class_companion_swapped.svg");
    Icon TRAIT_COMPANION = getIcon("/org/jetbrains/plugins/scala/images/trait_companion.svg");
    Icon TRAIT_COMPANION_SWAPPED = getIcon("/org/jetbrains/plugins/scala/images/trait_companion_swapped.svg");
    Icon OBECT_COMPANION = getIcon("/org/jetbrains/plugins/scala/images/object_companion.svg");
    Icon OBECT_COMPANION_SWAPPED = getIcon("/org/jetbrains/plugins/scala/images/object_companion_swapped.svg");

    //Internal nodes
    Icon FIELD_VAR = getIcon("/org/jetbrains/plugins/scala/images/field_variable.svg");
    Icon ABSTRACT_FIELD_VAR = getIcon("/org/jetbrains/plugins/scala/images/abstract_field_variable.svg");
    Icon FIELD_VAL = getIcon("/org/jetbrains/plugins/scala/images/field_value.svg");
    Icon ABSTRACT_FIELD_VAL = getIcon("/org/jetbrains/plugins/scala/images/abstract_field_value.svg");
    Icon TYPE_ALIAS = getIcon("/org/jetbrains/plugins/scala/images/type_alias.svg");
    Icon ABSTRACT_TYPE_ALIAS = getIcon("/org/jetbrains/plugins/scala/images/abstract_type_alias.svg");
    Icon FUNCTION = getIcon("/org/jetbrains/plugins/scala/images/function.svg");
    Icon VAR = getIcon("/org/jetbrains/plugins/scala/images/variable.svg");
    Icon VAL = getIcon("/org/jetbrains/plugins/scala/images/value.svg");
    Icon PARAMETER = getIcon("/org/jetbrains/plugins/scala/images/parameter.svg");
    Icon PATTERN_VAL = getIcon("/org/jetbrains/plugins/scala/images/pattern_value.svg");
    Icon LAMBDA = getIcon("/org/jetbrains/plugins/scala/images/lambda.svg");
    Icon RECURSION = getIcon("/org/jetbrains/plugins/scala/images/recursion.svg");
    Icon TAIL_RECURSION = getIcon("/org/jetbrains/plugins/scala/images/tail-recursion.svg");

    //Testing support
    Icon SCALA_TEST = getIcon("/org/jetbrains/plugins/scala/images/scala_test_icon.svg");
    Icon SCALA_TEST_NODE = getIcon("/org/jetbrains/plugins/scala/images/test.svg");

    //Console
    Icon SCALA_CONSOLE = getIcon("/org/jetbrains/plugins/scala/images/scala_console.svg");

    // Highlighting (status bar)
    Icon TYPED = getIcon("/org/jetbrains/plugins/scala/images/typed.svg");
    Icon UNTYPED = getIcon("/org/jetbrains/plugins/scala/images/untyped.svg");

    Icon LIGHTBEND_LOGO = getIcon("/org/jetbrains/plugins/scala/images/lightbend_logo.svg");

    // sbt
    Icon SBT = getIcon("/org/jetbrains/plugins/scala/images/sbt_icon.svg");
    // used from SBT.xml
    Icon SBT_TOOLWINDOW = getIcon("/org/jetbrains/plugins/scala/images/sbt_toolwin.svg");
    Icon SBT_FOLDER = getIcon("/org/jetbrains/plugins/scala/images/sbt_folder.svg");
    Icon SBT_SHELL = getIcon("/org/jetbrains/plugins/scala/images/sbt_shell.svg");
    // used from SBT.xml
    Icon SBT_SHELL_TOOLWINDOW = getIcon("/org/jetbrains/plugins/scala/images/sbt_shell_toolwin.svg");
    Icon SBT_LOAD_CHANGES = getIcon("/org/jetbrains/plugins/scala/images/sbtLoadChanges.svg");

}
