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

import com.intellij.debugger.DebuggerManager;
import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.plugins.scala.debugger.ScalaJVMNameMapper;

/**
 * @author ilyas
 */
public class ScalaLoader implements ApplicationInitializedListener { //todo: to remove?

  public void componentsInitialized() {
    if (!isUnderUpsource()) {
      loadScala();
    }
  }

  public static void loadScala() {
    MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
    messageBus.connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      public void projectOpened(Project project) {

        CompilerManager compilerManager = CompilerManager.getInstance(project);
        compilerManager.addCompilableFileType(ScalaFileType.INSTANCE);

        DebuggerManager.getInstance(project).addClassNameMapper(new ScalaJVMNameMapper());
      }
    });
  }

  public static boolean isUnderUpsource() {
    // The following check is hardly bulletproof, however (currently) there is no API to query that
    return ApplicationManager.getApplication().getClass().getSimpleName().contains("Upsource");
  }
}