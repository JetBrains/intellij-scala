package org.jetbrains.plugins.scala.gotoclass;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import org.jetbrains.plugins.scala.finder.ScalaSourceFilterScope;
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys;

import java.util.Collection;

/**
 * @author ilyas
 */
public class ScalaGoToClassContributor implements ChooseByNameContributor {
  public String[] getNames(Project project, boolean includeNonProjectItems) {
    final Collection<String> classNames = StubIndex.getInstance().getAllKeys(ScalaIndexKeys.SHORT_NAME_KEY(), project);
    return classNames.toArray(new String[classNames.size()]);
  }

  public NavigationItem[] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
    final GlobalSearchScope scope = includeNonProjectItems ? GlobalSearchScope.allScope(project) : GlobalSearchScope.projectScope(project);
    final Collection<PsiClass> classes = StubIndex.getInstance().get(ScalaIndexKeys.SHORT_NAME_KEY(), name, project, new ScalaSourceFilterScope(scope, project));
    return classes.toArray(new NavigationItem[classes.size()]);
  }
}