package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass;

import java.nio.file.Path;

public class ResolveClassWild3Test extends ScalaResolveTestCase {
  @Override
  public Path folderPath() {
    return super.folderPath().resolve("resolve").resolve("class").resolve("wild3");
  }

  public void testA() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScClass);
    assertEquals("AAA.CaseClass", ((ScClass) resolved).qualifiedName());
  }
}
