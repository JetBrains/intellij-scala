package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;

import java.nio.file.Path;

public class PackageObjectResolveTest extends ScalaResolveTestCase{

  @Override
  public Path folderPath() {
    return super.folderPath().resolve("resolve").resolve("packageObject");
  }

  public void testscalaCollectionFullyQualified() {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof ScTypeDefinition);
  }

  public void testscalaCollectionViaPackageObject() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTypeAlias);
  }
}
