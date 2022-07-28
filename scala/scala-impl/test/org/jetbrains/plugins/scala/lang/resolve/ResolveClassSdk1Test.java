package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition;

public class ResolveClassSdk1Test extends ScalaResolveTestCase {
  @Override
  public String folderPath() {
    return super.folderPath() + "resolve/class/sdk1/";
  }

  public void testsdk1() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTypeAliasDefinition);
    assertEquals(((ScTypeAliasDefinition) resolved).getName(), "Traversable");
  }
}
