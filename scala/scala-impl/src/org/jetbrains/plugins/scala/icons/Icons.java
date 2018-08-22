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

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * @author ilyas
 */
public interface Icons {

  Icon COMPILE_SERVER = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/compileServer.png");

  Icon FILE_TYPE_LOGO = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/scala16.png");
  //todo worksheet logo
  Icon WORKSHEET_LOGO = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/file_scala.png");
  Icon SCALA_SMALL_LOGO = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/scala-small-logo.png");
  Icon SCRIPT_FILE_LOGO = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/scala_script_icon.png");

  Icon ADD_CLAUSE = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/AddClause.png");
  Icon REMOVE_CLAUSE = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/RemoveClause.png");

  //SDK configuration
  Icon SCALA_SDK = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/scala_sdk.png");
  Icon NO_SCALA_SDK = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/no_scala_sdk.png");
  
  //Toplevel nodes
  Icon FILE = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/file_scala.png");
  Icon CLASS = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/class_scala.png");
  Icon ABSTRACT_CLASS = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/abstract_class_scala.png");
  Icon TRAIT = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/trait_scala.png");
  Icon OBJECT = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/object_scala.png");
  Icon PACKAGE_OBJECT = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/package_object.png");
  Icon CLASS_AND_OBJECT = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/class_object_scala.png");
  Icon ABSTRACT_CLASS_AND_OBJECT = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/abstract_class_object_scala.png");
  Icon TRAIT_AND_OBJECT = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/trait_object_scala.png");

  //Internal nodes
  Icon FIELD_VAR = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/field_variable.png");
  Icon ABSTRACT_FIELD_VAR = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/abstract_field_variable.png");
  Icon FIELD_VAL = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/field_value.png");
  Icon ABSTRACT_FIELD_VAL = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/abstract_field_value.png");
  Icon TYPE_ALIAS = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/type_alias.png");
  Icon ABSTRACT_TYPE_ALIAS = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/abstract_type_alias.png");
  Icon FUNCTION = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/function.png");
  Icon VAR = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/variable.png");
  Icon VAL = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/value.png");
  Icon PARAMETER = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/parameter.png");
  Icon PATTERN_VAL = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/pattern_value.png");
  Icon LAMBDA = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/lambda.png");

  //Testing support
  Icon SCALA_TEST = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/scala_test_icon.png");
  Icon SCALA_TEST_NODE = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/test.svg");

  //Console
  Icon SCALA_CONSOLE = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/scala_console.png");

  // Highlighting (status bar)
  Icon TYPED = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/typed.png");
  Icon UNTYPED = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/untyped.png");

  Icon ERROR = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/error.png");
  Icon WARNING = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/warning.png");

  Icon LIGHTBEND_LOGO = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/lightbend_logo.png");

  // sbt
  Icon SBT = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/sbt_icon.png");
  // used from SBT.xml
  Icon SBT_TOOLWINDOW = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/sbt_toolwin.png");
  Icon SBT_FILE = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/sbt_file.png");
  Icon SBT_FOLDER = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/sbt_folder.png");
  Icon SBT_SHELL = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/sbt_shell.png");
  // used from SBT.xml
  Icon SBT_SHELL_TOOLWINDOW = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/sbt_shell_toolwin.png");

}
