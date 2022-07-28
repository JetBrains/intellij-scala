package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor;

public class ResolveClassWildTest extends ScalaResolveTestCase {
  @Override
  public String folderPath() {
    return super.folderPath() + "resolve/class/wild";
  }

  public void testA() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScPrimaryConstructor);
  }
}
