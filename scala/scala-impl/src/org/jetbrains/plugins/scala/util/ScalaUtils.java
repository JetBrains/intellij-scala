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

package org.jetbrains.plugins.scala.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;

/**
 * @author Ilya.Sergey
 */
@SuppressWarnings({"AbstractClassNeverImplemented"})
public abstract class ScalaUtils {
  /**
   * This is name for type parameter like: .isInstanceOf[T]
   * This name should be unique, nobody can't use such name (it means that it has small probability).
   * In presentable text should be replace for T. So this string only for internal usage.
   */

  public static void runWriteAction(final Runnable runnable, Project project, String name) {
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(runnable);
      }
    }, name, null);
  }
}
