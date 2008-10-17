package org.jetbrains.plugins.scala.gotoclass;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;

import java.util.Collection;

import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys;

/**
 * User: Alexander Podkhalyuzin
 * Date: 14.10.2008
 */
public class ScalaGoToSymbolContributor implements ChooseByNameContributor {
  public String[] getNames(Project project, boolean includeNonProjectItems) {
    final Collection<String> methodNames = StubIndex.getInstance().getAllKeys(ScalaIndexKeys.METHOD_NAME_KEY());
    methodNames.addAll(StubIndex.getInstance().getAllKeys(ScalaIndexKeys.VALUE_NAME_KEY()));
    return methodNames.toArray(new String[methodNames.size()]);
  }

  public NavigationItem[] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
    final GlobalSearchScope scope = includeNonProjectItems ? null : GlobalSearchScope.projectScope(project);
    final Collection<PsiMember> methods = StubIndex.getInstance().get(ScalaIndexKeys.METHOD_NAME_KEY(), name, project, scope);
    methods.addAll(StubIndex.getInstance().get(ScalaIndexKeys.VALUE_NAME_KEY(), name, project, scope));
    return methods.toArray(new NavigationItem[methods.size()]);
  }
}
