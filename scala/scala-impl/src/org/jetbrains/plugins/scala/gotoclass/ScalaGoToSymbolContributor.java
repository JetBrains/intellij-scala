package org.jetbrains.plugins.scala.gotoclass;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.finder.ScalaFilterScope;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable;
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody;
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil;
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings;

import java.util.ArrayList;
import java.util.Collection;

import static org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.*;

/**
 * User: Alexander Podkhalyuzin
 * Date: 14.10.2008
 */
public class ScalaGoToSymbolContributor implements ChooseByNameContributor {
  @NotNull
  public String[] getNames(Project project, boolean includeNonProjectItems) {
    StubIndex stubIndex = StubIndex.getInstance();
    ArrayList<String> keys = new ArrayList<>();

    keys.addAll(stubIndex.getAllKeys(METHOD_NAME_KEY(), project));
    keys.addAll(stubIndex.getAllKeys(VALUE_NAME_KEY(), project));
    keys.addAll(stubIndex.getAllKeys(VARIABLE_NAME_KEY(), project));
    keys.addAll(stubIndex.getAllKeys(CLASS_PARAMETER_NAME_KEY(), project));
    keys.addAll(stubIndex.getAllKeys(TYPE_ALIAS_NAME_KEY(), project));
    keys.addAll(stubIndex.getAllKeys(NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY(), project));

    return keys.toArray(new String[0]);
  }

  @NotNull
  public NavigationItem[] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
    final boolean searchAll = ScalaProjectSettings.getInstance(project).isSearchAllSymbols();
    final GlobalSearchScope scope = includeNonProjectItems ? GlobalSearchScope.allScope(project) : GlobalSearchScope.projectScope(project);
    String cleanName = ScalaNamesUtil.cleanFqn(name);
    Collection<ScFunction> methods = getElements(METHOD_NAME_KEY(), cleanName, project, scope, ScFunction.class);
    Collection<ScTypeAlias> types = getElements(TYPE_ALIAS_NAME_KEY(), cleanName, project, scope, ScTypeAlias.class);

    ArrayList<NavigationItem> items = new ArrayList<>();

    if (searchAll) {
      items.addAll(methods);
      items.addAll(types);
    }
    else {
      for (ScFunction method : methods) {
        if (isNonLocal(method)) items.add(method);
      }
      for (ScTypeAlias type : types) {
        if (isNonLocal(type)) items.add(type);
      }
    }
    for (ScValue value : getElements(VALUE_NAME_KEY(), cleanName, project, scope, ScValue.class)) {
      if (isNonLocal(value) || searchAll) {
        final PsiNamedElement[] elems = value.declaredElementsArray();
        for (PsiNamedElement elem : elems) {
          if (elem instanceof NavigationItem) {
            String navigationItemName = ScalaNamesUtil.scalaName(elem);
            if (ScalaNamesUtil.equivalentFqn(name, navigationItemName)) items.add((NavigationItem) elem);
          }
        }
      }
    }

    for (ScVariable var : getElements(VARIABLE_NAME_KEY(), cleanName, project, scope, ScVariable.class)) {
      if (isNonLocal(var) || searchAll) {
        final PsiNamedElement[] elems = var.declaredElementsArray();
        for (PsiNamedElement elem : elems) {
          if (elem instanceof NavigationItem) {
            String navigationItemName = ScalaNamesUtil.scalaName(elem);
            if (ScalaNamesUtil.equivalentFqn(name, navigationItemName)) items.add((NavigationItem) elem);
          }
        }
      }
    }

    for (PsiClass clazz : getElements(NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY(), cleanName, project, scope, PsiClass.class)) {
      if (isNonLocal(clazz) || searchAll) {
        String navigationItemName = ScalaNamesUtil.scalaName(clazz);
        if (ScalaNamesUtil.equivalentFqn(name, navigationItemName)) items.add(clazz);
      }
    }

    items.addAll(getElements(CLASS_PARAMETER_NAME_KEY(), cleanName, project, scope, ScClassParameter.class));

    return items.toArray(new NavigationItem[0]);
  }

  private static boolean isNonLocal(NavigationItem item) {
    if (item instanceof PsiElement) {
      PsiElement parent = ((PsiElement) item).getParent();
      return parent instanceof ScTemplateBody || parent instanceof ScEarlyDefinitions;
    }
    return false;
  }

  private static <T extends PsiElement> Collection<T> getElements(StubIndexKey<String, T> indexKey,
                                                                  String cleanName,
                                                                  Project project,
                                                                  GlobalSearchScope scope,
                                                                  Class<T> requiredClass) {
    return StubIndex.getElements(indexKey, cleanName, project, new ScalaFilterScope(scope, project), requiredClass);
  }
}
