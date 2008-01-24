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

package org.jetbrains.plugins.scala;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.codeInsight.completion.*;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.debugger.DebuggerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.compiler.ScalaCompiler;
import org.jetbrains.plugins.scala.util.ScalaToolsFactory;

/**
 * Author: Ilya Sergey
 * Date: 20.09.2006
 * Time: 16:31:20
 */
public class ScalaLoader implements ApplicationComponent {
  public ScalaLoader() {}

  public void initComponent() {
    loadScala();
  }

  public static void loadScala() {
    ApplicationManager.getApplication().runWriteAction(
            new Runnable() {
              public void run() {
                FileTypeManager.getInstance().registerFileType(ScalaFileType.SCALA_FILE_TYPE, "scala");
              }
            }
    );


    CompletionUtil.registerCompletionData(ScalaFileType.SCALA_FILE_TYPE,
            ScalaToolsFactory.getInstance().createScalaCompletionData());

    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      public void projectOpened(Project project) {
        CompilerManager compilerManager = CompilerManager.getInstance(project);
        compilerManager.addCompiler(new ScalaCompiler(project));
        compilerManager.addCompilableFileType(ScalaFileType.SCALA_FILE_TYPE);

        DebuggerManager.getInstance(project).addClassNameMapper(ScalaToolsFactory.getInstance().createJVMNameMapper());
      }
    });
    

  }

  public void disposeComponent() {}

  @NotNull
  public String getComponentName() {
    return "Scala Loader";
  }
}