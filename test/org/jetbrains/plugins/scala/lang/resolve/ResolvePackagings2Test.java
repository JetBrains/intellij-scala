package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor;

/**
 * User: Alexander Podkhalyuzin
 * Date: 01.11.11
 */
public class ResolvePackagings2Test extends ScalaResolveTestCase {
  public String folderPath() {
    return super.folderPath() + "resolve/packages/separated/my/scala/stuff/";
  }

  @Override
  protected String rootPath() {
    return super.folderPath() + "resolve/packages/";
  }

  public void testMain() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    final PsiElement psiElement = ref.resolve();
    assertTrue(psiElement instanceof ScPrimaryConstructor);
    final ScPrimaryConstructor aClass = (ScPrimaryConstructor) psiElement;
    assertEquals(aClass.containingClass().qualifiedName(), "my.scala.List");
  }
}
