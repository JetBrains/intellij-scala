package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;

public class PackageObjectResolveTest extends ScalaResolveTestCase{

  @Override
  public String folderPath() {
    return super.folderPath() + "resolve/packageObject/";
  }

  public void testscalaCollectionFullyQualified() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof ScTypeDefinition);
  }

  public void testscalaCollectionViaPackageObject() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTypeAlias);
  }
}