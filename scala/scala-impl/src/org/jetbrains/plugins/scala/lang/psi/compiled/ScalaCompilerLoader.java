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

package org.jetbrains.plugins.scala.lang.psi.compiled;

import com.intellij.debugger.DebuggerManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition;

import static com.intellij.openapi.application.ApplicationManager.getApplication;

public class ScalaCompilerLoader implements StartupActivity.DumbAware {

    @Override
    public void runActivity(@NotNull Project project) {
        if (isDisabled()) return;

        CompilerManager.getInstance(project).addCompilableFileType(ScalaFileType.INSTANCE);

        DebuggerManager.getInstance(project).addClassNameMapper(clazz ->
                getApplication().runReadAction((Computable<String>) () ->
                        clazz instanceof ScTemplateDefinition ?
                                javaName((ScTemplateDefinition) clazz) :
                                null
                )
        );
    }

    static boolean isDisabled() {
        Application application = getApplication();
        return !application.isUnitTestMode() &&
                // The following check is hardly bulletproof, however (currently) there is no API to query that
                application.getClass().getSimpleName().contains("Upsource");
    }

    @NotNull
    private String javaName(@NotNull ScTemplateDefinition clazz) {
        return clazz.qualifiedName() + (clazz instanceof ScObject ? "$" : "");
    }
}