package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition;

import java.nio.file.Path;

public class ResolveClassSdk1Test extends ScalaResolveTestCase {
  @Override
  public Path folderPath() {
    return super.folderPath().resolve("resolve").resolve("class").resolve("sdk1");
  }

  public void testsdk1() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTypeAliasDefinition);
    assertEquals("Traversable", ((ScTypeAliasDefinition) resolved).getName());
  }
}
