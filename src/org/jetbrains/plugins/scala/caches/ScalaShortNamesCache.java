package org.jetbrains.plugins.scala.caches;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author ilyas
 */
public class ScalaShortNamesCache extends PsiShortNamesCache {

  Project myProject;

  public ScalaShortNamesCache(Project project) {
    myProject = project;
  }

  @NotNull
  public PsiClass[] getClassesByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    final Collection<? extends PsiClass> plainClasses = StubIndex.getInstance().get(ScalaIndexKeys.SHORT_NAME_KEY(), name, myProject, scope);
    return plainClasses.toArray(new PsiClass[plainClasses.size()]);
  }

  @Nullable
  public PsiClass getClassByFQName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    final Collection<? extends PsiClass> classes = StubIndex.getInstance().get(ScalaIndexKeys.FQN_KEY(), name.hashCode(), myProject, scope);
    for (PsiClass clazz : classes) {
      if (name.equals(clazz.getQualifiedName())) {
        if (clazz.getContainingFile() instanceof ScalaFile) {
          ScalaFile file = (ScalaFile) clazz.getContainingFile();
          if (file.isScriptFile()) continue;
        }
        return clazz;
      }
    }
    return null;
  }

  @NotNull
  public PsiClass[] getClassesByFQName(@NotNull @NonNls String fqn, @NotNull GlobalSearchScope scope) {
    final Collection<PsiClass> classes = StubIndex.getInstance().get(ScalaIndexKeys.FQN_KEY(), fqn.hashCode(), myProject, scope);
    ArrayList<PsiClass> list = new ArrayList<PsiClass>();
    for (PsiClass psiClass : classes) {
      if (fqn.equals(psiClass.getQualifiedName())) {
        list.add(psiClass);
      }
    }
    return list.toArray(new PsiClass[list.size()]);
  }

  public ScTypeDefinition getPackageObjectByName(@NotNull @NonNls String fqn, @NotNull GlobalSearchScope scope) {
    final Collection<PsiClass> classes = StubIndex.getInstance().get(ScalaIndexKeys.PACKAGE_OBJECT_KEY(), fqn.hashCode(), myProject, scope);
    ArrayList<ScTypeDefinition> list = new ArrayList<ScTypeDefinition>();
    for (PsiClass psiClass : classes) {
      if (fqn.equals(psiClass.getQualifiedName())) {
        if (psiClass instanceof ScTypeDefinition) {
          return ((ScTypeDefinition) psiClass);
        }
      }
    }
    return null;
  }

  @NotNull
  public String[] getAllClassNames() {
    final Collection<String> classNames = StubIndex.getInstance().getAllKeys(ScalaIndexKeys.SHORT_NAME_KEY(), myProject);
    return classNames.toArray(new String[classNames.size()]);
  }


  public void getAllClassNames(@NotNull HashSet<String> dest) {
    final Collection<String> classNames = StubIndex.getInstance().getAllKeys(ScalaIndexKeys.SHORT_NAME_KEY(), myProject);
  }

  @NotNull
  public PsiMethod[] getMethodsByName(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  public PsiMethod[] getMethodsByNameIfNotMoreThan(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope, int maxCount) {
    return getMethodsByName(name, scope);
  }

  @NotNull
  public String[] getAllMethodNames() {
    /*final Collection<String> classNames = StubIndex.getInstance().getAllKeys(ScalaIndexKeys.METHOD_NAME_KEY());
    return classNames.toArray(new String[classNames.size()]);*/
    return new String[0];
  }

  public void getAllMethodNames(@NotNull HashSet<String> set) {
    //todo implement me!
  }

  @NotNull
  public PsiField[] getFieldsByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    //todo implement me!
    return PsiField.EMPTY_ARRAY;
  }

  @NotNull
  public String[] getAllFieldNames() {
    //todo implement me!
    return new String[0];
  }

  public void getAllFieldNames(@NotNull HashSet<String> set) {
    //todo implement me!
  }

}