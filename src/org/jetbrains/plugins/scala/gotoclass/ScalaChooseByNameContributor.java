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
import org.jetbrains.plugins.scala.components.ScalaComponents;

import java.util.ArrayList;


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
    if (acc.size() > 0) {
      return acc.toArray(new String[acc.size()]);
    } else {
      return new String[0];
    }
  }

  public NavigationItem[] getItemsByName(String name, Project project, boolean includeNonProjectItems) {
    final GlobalSearchScope scope = includeNonProjectItems ? GlobalSearchScope.allScope(project) : GlobalSearchScope.projectScope(project);



    return filterUnshowable(PsiManager.getInstance(project).getShortNamesCache().getClassesByName(name, scope));
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
