package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass;

import java.nio.file.Path;

public class ResolveClassDependent2Test extends ScalaResolveTestCase {
  @Override
  public Path folderPath() {
    return super.folderPath().resolve("resolve").resolve("class").resolve("dependent2");
  }

  public void testa() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScClass);
  }
}
