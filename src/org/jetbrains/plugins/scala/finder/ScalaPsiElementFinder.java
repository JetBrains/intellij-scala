/*
*/
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
/*

package org.jetbrains.plugins.scala.finder;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.components.ScalaComponents;
import org.jetbrains.plugins.scala.caches.ScalaFilesCache;

import java.util.ArrayList;
import java.util.Collection;

*/
/**
 * @author Ilya.Sergey
 */
/*
public class ScalaPsiElementFinder implements PsiElementFinder, ProjectComponent {

  private Project myProject;


  public ScalaPsiElementFinder(Project project) {
    myProject = project;
  }

  public PsiClass findClass(@NotNull String qualifiedName, GlobalSearchScope scope) {

    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      ScalaModuleCachesManager manager =
              (ScalaModuleCachesManager) module.getComponent(ScalaComponents.SCALA_CACHE_MANAGER);
      ScalaModuleCaches caches = manager.getModuleFilesCache();
      PsiClass clazz;
      if ((clazz = caches.getClassByName(qualifiedName)) != null) {
        return clazz;
      }
    }

    //TODO implement scope

    Collection<ScalaFilesCache> sdkCaches = ((ScalaSDKCachesManager) myProject.getComponent(ScalaComponents.SCALA_SDK_CACHE_MANAGER)).
            getSdkFilesCaches().values();
    for (ScalaFilesCache sdkCache : sdkCaches) {
      PsiClass clazz;
      if ((clazz = sdkCache.getClassByName(qualifiedName)) != null) {
        return clazz;
      }
    }

    Collection<ScalaFilesCache> libraryCaches = ((ScalaLibraryCachesManager) myProject.getComponent(ScalaComponents.SCALA_LIBRARY_CACHE_MANAGER)).
            getLibraryFilesCaches().values();
    for (ScalaFilesCache libraryCache : libraryCaches) {
      PsiClass clazz;
      if ((clazz = libraryCache.getClassByName(qualifiedName)) != null) {
        return clazz;
      }
    }

    return null;
  }

  @NotNull
  public PsiClass[] findClasses(String qualifiedName, GlobalSearchScope scope) {
    ArrayList<PsiClass> classesAcc = new ArrayList<PsiClass>();
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      ScalaModuleCachesManager manager =
              (ScalaModuleCachesManager) module.getComponent(ScalaComponents.SCALA_CACHE_MANAGER);
      ScalaModuleCaches caches = manager.getModuleFilesCache();
      PsiClass[] classes = caches.getClassesByName(qualifiedName);
      for (PsiClass clazz : classes) {
        classesAcc.add(clazz);
      }
    }

    //TODO implement scope
    Collection<ScalaFilesCache> sdkCaches = ((ScalaSDKCachesManager) myProject.getComponent(ScalaComponents.SCALA_SDK_CACHE_MANAGER)).
            getSdkFilesCaches().values();
    for (ScalaFilesCache sdkCache : sdkCaches) {
      PsiClass[] classes = sdkCache.getClassesByName(qualifiedName);
      for (PsiClass clazz : classes) {
        classesAcc.add(clazz);
      }
    }

    Collection<ScalaFilesCache> libraryCaches = ((ScalaLibraryCachesManager) myProject.getComponent(ScalaComponents.SCALA_LIBRARY_CACHE_MANAGER)).
            getLibraryFilesCaches().values();
    for (ScalaFilesCache libCache : libraryCaches) {
      PsiClass[] classes = libCache.getClassesByName(qualifiedName);
      for (PsiClass clazz : classes) {
        classesAcc.add(clazz);
      }
    }

    return classesAcc.toArray(PsiClass.EMPTY_ARRAY);
  }

  public PsiPackage findPackage(String qualifiedName) {
    return null;
  }

  @NotNull
  public PsiPackage[] getSubPackages(PsiPackage psiPackage, GlobalSearchScope scope) {
    return new PsiPackage[0];
  }

  @NotNull
  public PsiClass[] getClasses(PsiPackage psiPackage, GlobalSearchScope scope) {
    ArrayList<PsiClass> list = new ArrayList<PsiClass>();
    final PsiDirectory[] dirs = psiPackage.getDirectories(scope);
*/
/*
    for (PsiDirectory dir : dirs) {
      PsiClass[] classes = dir.get;
      for (PsiClass aClass : classes) {
        list.add(aClass);
      }
    }
*/
/*
    return list.toArray(new PsiClass[list.size()]);
  }

  public void projectOpened() {

  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return ScalaComponents.SCALA_PSI_ELEMENT_FINDER;
  }

  public void initComponent() {
  }

  public void disposeComponent() {

  }
}
*/
