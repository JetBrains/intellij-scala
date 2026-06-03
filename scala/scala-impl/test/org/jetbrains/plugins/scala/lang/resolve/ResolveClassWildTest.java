package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor;

import java.nio.file.Path;

public class ResolveClassWildTest extends ScalaResolveTestCase {
  @Override
  public Path folderPath() {
    return super.folderPath().resolve("resolve").resolve("class").resolve("wild");
  }

  public void testA() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScPrimaryConstructor);
  }
}
