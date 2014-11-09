package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass;

/**
 * User: Alexander Podkhalyuzin
 * Date: 01.11.11
 */
public class ResolveClassDependentTest extends ScalaResolveTestCase {
  public String folderPath() {
    return super.folderPath() + "resolve/class/dependent/";
  }

  public void testMainXMLExporter() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScClass);
  }
}
