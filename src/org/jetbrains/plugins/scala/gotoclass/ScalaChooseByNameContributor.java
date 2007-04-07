/*
 * Copyright 2000-2006 JetBrains s.r.o.
 *
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.plugins.scala.cache.module.ScalaModuleCachesManager;
import org.jetbrains.plugins.scala.cache.module.ScalaModuleCaches;
import org.jetbrains.plugins.scala.cache.ScalaFilesCache;
import org.jetbrains.plugins.scala.cache.ScalaSDKCachesManager;
import org.jetbrains.plugins.scala.cache.ScalaLibraryCachesManager;
import org.jetbrains.plugins.scala.components.ScalaComponents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;


/**
 * Allows a plugin to add items to "Goto Class" and "Goto Symbol" lists.
 *
 * @author Ilya.Sergey
 */
public class ScalaChooseByNameContributor implements ChooseByNameContributor {

  public String[] getNames(Project project, boolean includeNonProjectItems) {
    ArrayList<String> acc = new ArrayList<String>();
    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      ScalaModuleCachesManager manager =
              (ScalaModuleCachesManager) module.getComponent(ScalaComponents.SCALA_CACHE_MANAGER);
      ScalaModuleCaches caches = manager.getModuleFilesCache();
      acc.addAll(caches.getAllClassShortNames());
    }

    if (includeNonProjectItems) {
      Collection<ScalaFilesCache> sdkCaches = ((ScalaSDKCachesManager) project.getComponent(ScalaComponents.SCALA_SDK_CACHE_MANAGER)).
              getSdkFilesCaches().values();
      for (ScalaFilesCache sdkCache : sdkCaches) {
        acc.addAll(sdkCache.getAllClassShortNames());
      }

      Collection<ScalaFilesCache> libCaches = ((ScalaLibraryCachesManager) project.getComponent(ScalaComponents.SCALA_LIBRARY_CACHE_MANAGER)).
              getLibraryFilesCaches().values();
      for (ScalaFilesCache libCache : libCaches) {
        acc.addAll(libCache.getAllClassShortNames());
      }
    }

    if (acc.size() > 0) {
      return acc.toArray(new String[acc.size()]);
    } else {
      return new String[0];
    }
  }

  public NavigationItem[] getItemsByName(String name, Project project, boolean includeNonProjectItems) {
    final GlobalSearchScope scope = includeNonProjectItems ? GlobalSearchScope.allScope(project) : GlobalSearchScope.projectScope(project);

    ArrayList<PsiClass> acc = new ArrayList<PsiClass>();
    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      ScalaModuleCachesManager manager =
              (ScalaModuleCachesManager) module.getComponent(ScalaComponents.SCALA_CACHE_MANAGER);
      ScalaModuleCaches caches = manager.getModuleFilesCache();
      acc.addAll(Arrays.asList(caches.getClassesByShortClassName(name)));
    }

    if (includeNonProjectItems) {
      Collection<ScalaFilesCache> sdkCaches = ((ScalaSDKCachesManager) project.getComponent(ScalaComponents.SCALA_SDK_CACHE_MANAGER)).
              getSdkFilesCaches().values();
      for (ScalaFilesCache sdkCache : sdkCaches) {
        acc.addAll(Arrays.asList(sdkCache.getClassesByShortClassName(name)));
      }

      Collection<ScalaFilesCache> libCaches = ((ScalaLibraryCachesManager) project.getComponent(ScalaComponents.SCALA_LIBRARY_CACHE_MANAGER)).
              getLibraryFilesCaches().values();
      for (ScalaFilesCache libCache : libCaches) {
        acc.addAll(Arrays.asList(libCache.getClassesByShortClassName(name)));
      }
    }

    PsiClass[] candidats = acc.toArray(PsiClass.EMPTY_ARRAY);
    return filterUnshowable(candidats);
  }

  private static NavigationItem[] filterUnshowable(PsiClass[] items) {
    ArrayList<NavigationItem> list = new ArrayList<NavigationItem>(items.length);
    for (PsiClass item : items) {
      if (item.getContainingFile().getVirtualFile() == null) continue;
      list.add(item);
    }
    return list.toArray(new NavigationItem[list.size()]);
  }

}
