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

package org.jetbrains.plugins.scala.caches.project;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.plugins.scala.caches.ScalaFilesCache;

/**
 * @author ilyas
 */
public abstract class ScalaCachesManager implements ProjectComponent {
  public static ScalaCachesManager getInstance(Project project) {
    return project.getComponent(ScalaCachesManager.class);
  }

  public abstract PsiClass getClassByName(String qName, GlobalSearchScope scope);

  public abstract PsiClass[] getClassesByName(String qName, GlobalSearchScope scope);

  public abstract PsiClass[] getDeriverCandidates(PsiClass baseClass, GlobalSearchScope scope);

  public abstract ScalaFilesCache getModuleFilesCache(Module module);
}
