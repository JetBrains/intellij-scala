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

package org.jetbrains.plugins.scala.gotoclass;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import org.jetbrains.plugins.scala.caches.project.ScalaCachesManager;
import org.jetbrains.plugins.scala.caches.project.ScalaCachesManagerImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;


/**
 * Allows a plugin to add items to "Goto Class" and "Goto Symbol" lists.
 *
 * @author Ilya.Sergey
 */
public class ScalaChooseByNameContributor implements ChooseByNameContributor {
  private static final String[] EMPTY_ARRAY = new String[0];

  public String[] getNames(Project project, boolean includeNonProjectItems) {
    ArrayList<String> acc = new ArrayList<String>();
    ScalaCachesManager manager = ScalaCachesManagerImpl.getInstance(project);
    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      final Collection<String> allShortNames = manager.getModuleFilesCache(module).getAllClassShortNames();
      acc.addAll(allShortNames);
    }

    if (acc.size() > 0) {
      return acc.toArray(new String[acc.size()]);
    } else {
      return EMPTY_ARRAY;
    }
  }

  public NavigationItem[] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
    ArrayList<PsiClass> acc = new ArrayList<PsiClass>();
    ScalaCachesManager manager = ScalaCachesManagerImpl.getInstance(project);
    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      final PsiClass[] classesByShortName = manager.getModuleFilesCache(module).getClassesByShortClassName(name);
      acc.addAll(Arrays.asList(classesByShortName));
    }

    return filterUnshowable(acc);
  }

  private static NavigationItem[] filterUnshowable(List<PsiClass> items) {
    ArrayList<NavigationItem> list = new ArrayList<NavigationItem>(items.size());
    for (PsiClass item : items) {
      if (item.getContainingFile().getVirtualFile() == null) continue;
      list.add(item);
    }
    return list.toArray(new NavigationItem[list.size()]);
  }

}
