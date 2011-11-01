package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;

/**
 * User: Alexander Podkhalyuzin
 * Date: 01.11.11
 */
public class ResolveClassLevelImportTest extends ScalaResolveTestCase {
  public String folderPath() {
    return super.folderPath() + "resolve/class/classLevelImport/";
  }

  public void testclassLevelImport() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiClass);
    assertEquals(((PsiClass) resolved).getQualifiedName(), "scala.collection.immutable.Map");
  }
}
