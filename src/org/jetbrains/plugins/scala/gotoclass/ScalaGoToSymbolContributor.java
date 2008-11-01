package org.jetbrains.plugins.scala.gotoclass;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiElement;

import java.util.Collection;
import java.util.ArrayList;

import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable;
import scala.Seq;

/**
 * User: Alexander Podkhalyuzin
 * Date: 14.10.2008
 */
public class ScalaGoToSymbolContributor implements ChooseByNameContributor {
  public String[] getNames(Project project, boolean includeNonProjectItems) {
    final Collection<String> items = StubIndex.getInstance().getAllKeys(ScalaIndexKeys.METHOD_NAME_KEY());
    items.addAll(StubIndex.getInstance().getAllKeys(ScalaIndexKeys.VALUE_NAME_KEY()));
    items.addAll(StubIndex.getInstance().getAllKeys(ScalaIndexKeys.VARIABLE_NAME_KEY()));
    items.addAll(StubIndex.getInstance().getAllKeys(ScalaIndexKeys.TYPE_ALIAS_NAME_KEY()));
    return items.toArray(new String[items.size()]);
  }

  public NavigationItem[] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
    final GlobalSearchScope scope = includeNonProjectItems ? null : GlobalSearchScope.projectScope(project);
    final Collection<? extends NavigationItem> methods = StubIndex.getInstance().get(ScalaIndexKeys.METHOD_NAME_KEY(), name, project, scope);
    final Collection<? extends NavigationItem> types = StubIndex.getInstance().get(ScalaIndexKeys.TYPE_ALIAS_NAME_KEY(), name, project, scope);
    final Collection<? extends NavigationItem> values = StubIndex.getInstance().get(ScalaIndexKeys.VALUE_NAME_KEY(), name, project, scope);
    final Collection<? extends NavigationItem> vars = StubIndex.getInstance().get(ScalaIndexKeys.VARIABLE_NAME_KEY(), name, project, scope);

    final ArrayList<NavigationItem> items = new ArrayList<NavigationItem>();

    items.addAll(methods);
    items.addAll(types);

    for (NavigationItem value : values) {
      if (value instanceof ScValue) {
        final ScValue el = (ScValue) value;
        final Seq seq = el.declaredElements();
        for (int i = 0; i < seq.length(); ++i) {
          final NavigationItem navigationItem = (NavigationItem) seq.apply(i);
          if (name.equals(navigationItem.getName())) items.add(navigationItem);
        }
      }
    }

    for (NavigationItem var : vars) {
      if (var instanceof ScVariable) {
        final ScVariable el = (ScVariable) var;
        final Seq seq = el.declaredElements();
        for (int i = 0; i < seq.length(); ++i) {
          final NavigationItem navigationItem = (NavigationItem) seq.apply(i);
          if (name.equals(navigationItem.getName())) items.add(navigationItem);
        }
      }
    }

    return items.toArray(new NavigationItem[items.size()]);
  }
}
