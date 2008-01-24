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

package org.jetbrains.plugins.scala.actions.creators;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.IconLoader;

/**
 * Author: Ilya Sergey
 * Date: 24.09.2006
 * Time: 18:03:32
 */
public class NewScalaFileCreate extends AnAction {

  public NewScalaFileCreate() {
    super("Scala file",
      "Create new Scala file",
      IconLoader.getIcon("/org/jetbrains/plugins/scala/images/scala_logo.png"));
  }

  public void actionPerformed(AnActionEvent e) {

  }
}
