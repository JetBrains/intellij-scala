/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.icons;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public interface Icons {

  Icon COMPILE_SERVER = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/compileServer.svg");

  Icon FILE_TYPE_LOGO = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/scala16.svg");
  //todo worksheet logo
  Icon WORKSHEET_LOGO = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/file_scala.svg");
  Icon SCALA_SMALL_LOGO = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/scala-small-logo.svg");
  Icon SCRIPT_FILE_LOGO = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/scala_script_icon.svg");

  Icon ADD_CLAUSE = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/AddClause.svg");
  Icon REMOVE_CLAUSE = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/RemoveClause.svg");

  //SDK configuration
  Icon SCALA_SDK = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/scala_sdk.svg");
  Icon NO_SCALA_SDK = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/no_scala_sdk.svg");
  
  //Toplevel nodes
  Icon FILE = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/file_scala.svg");
  Icon CLASS = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/class_scala.svg");
  Icon ABSTRACT_CLASS = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/abstract_class_scala.svg");
  Icon TRAIT = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/trait_scala.svg");
  Icon OBJECT = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/object_scala.svg");
  Icon PACKAGE_OBJECT = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/package_object.svg");
  Icon CLASS_AND_OBJECT = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/class_object_scala.svg");
  Icon ABSTRACT_CLASS_AND_OBJECT = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/abstract_class_object_scala.svg");
  Icon TRAIT_AND_OBJECT = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/trait_object_scala.svg");

  //Internal nodes
  Icon FIELD_VAR = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/field_variable.svg");
  Icon ABSTRACT_FIELD_VAR = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/abstract_field_variable.svg");
  Icon FIELD_VAL = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/field_value.svg");
  Icon ABSTRACT_FIELD_VAL = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/abstract_field_value.svg");
  Icon TYPE_ALIAS = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/type_alias.svg");
  Icon ABSTRACT_TYPE_ALIAS = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/abstract_type_alias.svg");
  Icon FUNCTION = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/function.svg");
  Icon VAR = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/variable.svg");
  Icon VAL = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/value.svg");
  Icon PARAMETER = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/parameter.svg");
  Icon PATTERN_VAL = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/pattern_value.svg");
  Icon LAMBDA = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/lambda.svg");

  //Testing support
  Icon SCALA_TEST = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/scala_test_icon.svg");
  Icon SCALA_TEST_NODE = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/test.svg");

  //Console
  Icon SCALA_CONSOLE = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/scala_console.svg");

  // Highlighting (status bar)
  Icon TYPED = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/typed.svg");
  Icon UNTYPED = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/untyped.svg");

  Icon ERROR = AllIcons.General.BalloonError;
  Icon WARNING = AllIcons.General.BalloonWarning;

  Icon LIGHTBEND_LOGO = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/lightbend_logo.svg");

  // sbt
  Icon SBT = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/sbt_icon.svg");
  // used from SBT.xml
  Icon SBT_TOOLWINDOW = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/sbt_toolwin.svg");
  Icon SBT_FILE = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/sbt_file.svg");
  Icon SBT_FOLDER = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/sbt_folder.svg");
  Icon SBT_SHELL = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/sbt_shell.svg");
  // used from SBT.xml
  Icon SBT_SHELL_TOOLWINDOW = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/sbt_shell_toolwin.svg");

}
