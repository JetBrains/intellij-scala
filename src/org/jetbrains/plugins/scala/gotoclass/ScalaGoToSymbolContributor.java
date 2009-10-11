package org.jetbrains.plugins.scala.gotoclass;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody;
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys;

import java.util.ArrayList;
import java.util.Collection;

/**
 * User: Alexander Podkhalyuzin
 * Date: 14.10.2008
 */
public class ScalaGoToSymbolContributor implements ChooseByNameContributor {
  public String[] getNames(Project project, boolean includeNonProjectItems) {
    final Collection<String> items = StubIndex.getInstance().getAllKeys(ScalaIndexKeys.METHOD_NAME_KEY(), project);
    items.addAll(StubIndex.getInstance().getAllKeys(ScalaIndexKeys.VALUE_NAME_KEY(), project));
    items.addAll(StubIndex.getInstance().getAllKeys(ScalaIndexKeys.VARIABLE_NAME_KEY(), project));
    items.addAll(StubIndex.getInstance().getAllKeys(ScalaIndexKeys.TYPE_ALIAS_NAME_KEY(), project));
    return items.toArray(new String[items.size()]);
  }

  public NavigationItem[] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
    final boolean searchAll = CodeStyleSettingsManager.getSettings(project).getCustomSettings(ScalaCodeStyleSettings.class).SEARCH_ALL_SYMBOLS;
    final GlobalSearchScope scope = includeNonProjectItems ? GlobalSearchScope.allScope(project) : GlobalSearchScope.projectScope(project);
    final Collection<? extends NavigationItem> methods = StubIndex.getInstance().get(ScalaIndexKeys.METHOD_NAME_KEY(), name, project, scope);
    final Collection<? extends NavigationItem> types = StubIndex.getInstance().get(ScalaIndexKeys.TYPE_ALIAS_NAME_KEY(), name, project, scope);
    final Collection<? extends NavigationItem> values = StubIndex.getInstance().get(ScalaIndexKeys.VALUE_NAME_KEY(), name, project, scope);
    final Collection<? extends NavigationItem> vars = StubIndex.getInstance().get(ScalaIndexKeys.VARIABLE_NAME_KEY(), name, project, scope);

    final ArrayList<NavigationItem> items = new ArrayList<NavigationItem>();

    if (searchAll) {
      items.addAll(methods);
      items.addAll(types);
    }
    else {
      for (NavigationItem method : methods) {
        if (!isLocal(method)) items.add(method);
      }
      for (NavigationItem type : types) {
        if (!isLocal(type)) items.add(type);
      }
    }
    for (NavigationItem value : values) {
      if (value instanceof ScValue && (!isLocal(value) || searchAll)) {
        final ScValue el = (ScValue) value;
        final PsiNamedElement[] elems = el.declaredElementsArray();
        for (PsiNamedElement elem : elems) {
          if (elem instanceof NavigationItem) {
            final NavigationItem navigationItem = (NavigationItem) elem;
            if (name.equals(navigationItem.getName())) items.add(navigationItem);
          }
        }
      }
    }

    for (NavigationItem var : vars) {
      if (var instanceof ScVariable && (!isLocal(var) || searchAll)) {
        final ScVariable el = (ScVariable) var;
        final PsiNamedElement[] elems = el.declaredElementsArray();
        for (PsiNamedElement elem : elems) {
          final NavigationItem navigationItem = (NavigationItem) elem;
          if (name.equals(navigationItem.getName())) items.add(navigationItem);
        }
      }
    }

    return items.toArray(new NavigationItem[items.size()]);
  }

  private boolean isLocal(NavigationItem item) {
    if (item instanceof PsiElement) {
      PsiElement element = (PsiElement) item;
      if (element.getParent() instanceof ScTemplateBody) {
        return false;
      }
    }
    return true;
  }
}
