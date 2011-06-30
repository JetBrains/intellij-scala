package org.jetbrains.plugins.scala.caches;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.containers.HashSet;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.finder.ScalaSourceFilterScope;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

/**
 * @author ilyas
 */
public class ScalaShortNamesCache extends PsiShortNamesCache {
  private Logger LOG = Logger.getInstance("#org.jetbrains.plugins.scala.caches.ScalaShortNamesCache");

  Project myProject;

  public ScalaShortNamesCache(Project project) {
    myProject = project;
  }

  @NotNull
  public PsiClass[] getClassesByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    final Collection<? extends PsiClass> plainClasses = StubIndex.getInstance().get(ScalaIndexKeys.SHORT_NAME_KEY(), name, myProject, new ScalaSourceFilterScope(scope, myProject));
    return plainClasses.toArray(new PsiClass[plainClasses.size()]);
  }

  @Nullable
  public PsiClass getClassByFQName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    final Collection<? extends PsiElement> classes = StubIndex.getInstance().get(ScalaIndexKeys.FQN_KEY(), name.hashCode(), myProject, new ScalaSourceFilterScope(scope, myProject));
    for (PsiElement element : classes) {
      if (!(element instanceof PsiClass)) {
        VirtualFile faultyContainer = PsiUtilBase.getVirtualFile(element);
        LOG.error("Wrong Psi in Psi list: " + faultyContainer);
        if (faultyContainer != null && faultyContainer.isValid()) {
          FileBasedIndex.getInstance().requestReindex(faultyContainer);
        }
        return null;
      }
      PsiClass clazz = (PsiClass) element;
      if (name.equals(clazz.getQualifiedName())) {
        if (clazz.getContainingFile() instanceof ScalaFile) {
          ScalaFile file = (ScalaFile) clazz.getContainingFile();
          if (file.isScriptFile(true)) continue;
        }
        return clazz;
      }
    }
    return null;
  }

  @NotNull
  public PsiClass[] getClassesByFQName(@NotNull @NonNls String fqn, @NotNull GlobalSearchScope scope) {
    final Collection<? extends PsiElement> classes = StubIndex.getInstance().get(ScalaIndexKeys.FQN_KEY(),
        fqn.hashCode(), myProject, new ScalaSourceFilterScope(scope, myProject));
    ArrayList<PsiClass> list = new ArrayList<PsiClass>();
    PsiClass psiClass = null;
    int count = 0;
    for (PsiElement element : classes) {
      if (!(element instanceof PsiClass)) {
        VirtualFile faultyContainer = PsiUtilBase.getVirtualFile(element);
        LOG.error("Wrong Psi in Psi list: " + faultyContainer);
        if (faultyContainer != null && faultyContainer.isValid()) {
          FileBasedIndex.getInstance().requestReindex(faultyContainer);
        }
        return PsiClass.EMPTY_ARRAY;
      }
      psiClass = (PsiClass) element;
      if (fqn.equals(psiClass.getQualifiedName())) {
        list.add(psiClass);
        count++;
      }
    }
    if (count == 0) return PsiClass.EMPTY_ARRAY;
    if (count == 1) return new PsiClass[]{psiClass};

    return list.toArray(new PsiClass[count]);
  }

  public ScTypeDefinition getPackageObjectByName(@NotNull @NonNls String fqn, @NotNull GlobalSearchScope scope) {
    final Collection<? extends PsiElement> classes = StubIndex.getInstance().get(ScalaIndexKeys.PACKAGE_OBJECT_KEY(), fqn.hashCode(),
        myProject, new ScalaSourceFilterScope(scope, myProject));
    for (PsiElement element: classes) {
      if (!(element instanceof PsiClass)) {
        VirtualFile faultyContainer = PsiUtilBase.getVirtualFile(element);
        LOG.error("Wrong Psi in Psi list: " + faultyContainer);
        if (faultyContainer != null && faultyContainer.isValid()) {
          FileBasedIndex.getInstance().requestReindex(faultyContainer);
        }
        return null;
      }
      PsiClass psiClass = (PsiClass) element;
      String qualifiedName = psiClass.getQualifiedName();
      if (psiClass.getName().equals("`package`"))
        qualifiedName = qualifiedName.substring(0, qualifiedName.lastIndexOf('.'));
      if (fqn.equals(qualifiedName)) {
        if (psiClass instanceof ScTypeDefinition) {
          return ((ScTypeDefinition) psiClass);
        }
      }
    }
    return null;
  }

  public ScObject[] getImplicitObjectsByPackage(@NotNull @NonNls String fqn, @NotNull GlobalSearchScope scope) {
    final Collection<? extends PsiElement> classes = StubIndex.getInstance().get(ScalaIndexKeys.IMPLICIT_OBJECT_KEY(), fqn,
        myProject, new ScalaSourceFilterScope(scope, myProject));
    ArrayList<ScObject> res = new ArrayList<ScObject>();
    for (PsiElement element: classes) {
      if (!(element instanceof ScObject)) {
        VirtualFile faultyContainer = PsiUtilBase.getVirtualFile(element);
        LOG.error("Wrong Psi in Psi list: " + faultyContainer);
        if (faultyContainer != null && faultyContainer.isValid()) {
          FileBasedIndex.getInstance().requestReindex(faultyContainer);
        }
        return new ScObject[0];
      }
      res.add((ScObject) element);
    }
    return res.toArray(new ScObject[res.size()]);
  }

  public Set<String> getClassNames(PsiPackage psiPackage, GlobalSearchScope scope) {
    if (DumbServiceImpl.getInstance(myProject).isDumb()) return new HashSet<String>();
    String qual = psiPackage.getQualifiedName();
    final Collection<? extends PsiElement> classes = StubIndex.getInstance().get(ScalaIndexKeys.CLASS_NAME_IN_PACKAGE_KEY(), qual,
        myProject, new ScalaSourceFilterScope(scope, myProject));
    HashSet<String> strings = new HashSet<String>();
    for (PsiElement element : classes) {
      if (!(element instanceof PsiClass)) {
        VirtualFile faultyContainer = PsiUtilBase.getVirtualFile(element);
        LOG.error("Wrong Psi in Psi list: " + faultyContainer);
        if (faultyContainer != null && faultyContainer.isValid()) {
          FileBasedIndex.getInstance().requestReindex(faultyContainer);
        }
        return null;
      }
      PsiClass clazz = (PsiClass) element;
      strings.add(clazz.getName());
    }
    return strings;
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
    return getMethodsByName(name, new ScalaSourceFilterScope(scope, myProject));
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