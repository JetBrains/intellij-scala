package org.jetbrains.plugins.scala.finder;

import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.HashMap;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.components.ScalaComponents;
import org.jetbrains.plugins.scala.cache.module.ScalaModuleCachesManager;
import org.jetbrains.plugins.scala.cache.module.ScalaModuleCaches;
import org.jetbrains.plugins.scala.cache.info.ScalaFileInfo;

import java.util.Map;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Ilya.Sergey
 */
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
      Collection<ScalaFileInfo> infos = caches.getAllClasses();
      for (ScalaFileInfo info : infos) {
        for (PsiClass clazz : info.getClasses()) {
          if (clazz.getQualifiedName().equals(qualifiedName)) {
            return clazz;
          }
        }
      }
    }
    return null;
  }

  @NotNull
  public PsiClass[] findClasses(String qualifiedName, GlobalSearchScope scope) {
    ArrayList<PsiClass> classes = new ArrayList<PsiClass>();
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      ScalaModuleCachesManager manager =
              (ScalaModuleCachesManager) module.getComponent(ScalaComponents.SCALA_CACHE_MANAGER);
      ScalaModuleCaches caches = manager.getModuleFilesCache();
      Collection<ScalaFileInfo> infos = caches.getAllClasses();
      for (ScalaFileInfo info : infos) {
        for (PsiClass clazz : info.getClasses()) {
          if (clazz.getQualifiedName().equals(qualifiedName)) {
            classes.add(clazz);
          }
        }
      }
    }
    return classes.toArray(new PsiClass[classes.size()]);
  }

  public PsiPackage findPackage(String qualifiedName) {
/*
    final PsiPackage aPackage = myFileManager.findPackage(qualifiedName);
    if (aPackage == null && myCurrentMigration != null) {
      final PsiPackage migrationPackage = myCurrentMigration.getMigrationPackage(qualifiedName);
      if (migrationPackage != null) return migrationPackage;
    }

    return aPackage;
*/
    return null;
  }

  @NotNull
  public PsiPackage[] getSubPackages(PsiPackage psiPackage, GlobalSearchScope scope) {
    final Map<String, PsiPackage> packagesMap = new HashMap<String, PsiPackage>();
    final String qualifiedName = psiPackage.getQualifiedName();
    final PsiDirectory[] dirs = psiPackage.getDirectories(scope);
    for (PsiDirectory dir : dirs) {
      PsiDirectory[] subdirs = dir.getSubdirectories();
      for (PsiDirectory subdir : subdirs) {
        final PsiPackage aPackage = subdir.getPackage();
        if (aPackage != null) {
          final String subQualifiedName = aPackage.getQualifiedName();
          if (subQualifiedName.startsWith(qualifiedName) && !packagesMap.containsKey(subQualifiedName)) {
            packagesMap.put(aPackage.getQualifiedName(), aPackage);
          }
        }
      }
    }
    return packagesMap.values().toArray(new PsiPackage[packagesMap.size()]);
  }

  @NotNull
  public PsiClass[] getClasses(PsiPackage psiPackage, GlobalSearchScope scope) {
    ArrayList<PsiClass> list = new ArrayList<PsiClass>();
    final PsiDirectory[] dirs = psiPackage.getDirectories(scope);
    for (PsiDirectory dir : dirs) {
      PsiClass[] classes = dir.getClasses();
      for (PsiClass aClass : classes) {
        list.add(aClass);
      }
    }
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
