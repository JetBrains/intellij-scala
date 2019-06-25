package org.jetbrains.plugins.scala.lang.resolve.aux1;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass;
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveTestCase;

/**
 * @author ilyas
 */
public class DependenciesResolveTest extends ScalaResolveTestCase {
  public String folderPath() {
    return super.folderPath() + "resolve/aux1/idea/";
  }

  @Override
  public String sourceRootPath() {
    return folderPath();
  }

  public void testLocalImport() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    final PsiElement resolved = ref.resolve();

    assertTrue(resolved instanceof ScPrimaryConstructor);

    final ScClass clazz = (ScClass) ((ScPrimaryConstructor) resolved).containingClass();
    final String name = clazz.getName();
    assertEquals("Description", name);

  }

  public void testLocalImport1() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    final PsiElement resolved = ref.resolve();

    assertTrue(resolved instanceof ScTypeAlias);

    final ScTypeAlias alias = (ScTypeAlias) resolved;
    final String name = alias.getName();
    assertEquals("Den", name);

  }

  public void testLocalImport2() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    final PsiElement resolved = ref.resolve();

    assertTrue(resolved instanceof PsiNamedElement);

    final PsiNamedElement named = (PsiNamedElement) resolved;
    final String name = named.getName();
    assertEquals("popa", name);
  }

}

