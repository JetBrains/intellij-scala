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

  Icon FILE_TYPE_LOGO = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/scala16.png");
  Icon SETTINGS_LOGO = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/scala32.png");
  Icon CLASS = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/class_scala.png");
  Icon TRAIT = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/trait_scala.png");
  Icon OBJECT = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/object_scala.png");
  Icon METHOD = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/method.png");
  Icon FUNCTION = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/function.png");
  Icon VAR = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/variable.png");
  Icon VAL = IconLoader.getIcon("/org/jetbrains/plugins/scala/nodes/constant.png");

}
