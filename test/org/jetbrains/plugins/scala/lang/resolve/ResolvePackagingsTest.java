package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor;

/**
 * @author ilyas
 */
public class ResolvePackagingsTest extends ScalaResolveTestCase {
  public String folderPath() {
    return super.folderPath() + "resolve/packages/solid/my/scala/stuff/";
  }

  @Override
  protected String rootPath() {
    return super.folderPath() + "resolve/packages/";
  }

  public void testMain() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    final PsiElement psiElement = ref.resolve();
    assertTrue(psiElement instanceof ScPrimaryConstructor);
  }

}
