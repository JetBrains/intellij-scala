package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition;

/**
 * User: Alexander Podkhalyuzin
 * Date: 01.11.11
 */
public class FunctionsTypeParam1ResolveTest extends ScalaResolveTestCase {
  public String folderPath() {
    return super.folderPath() + "resolve/functions/typeParam1/";
  }

  public void testtp1() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScFunctionDefinition);
    assertEquals(resolved.getText(), "def gul[A](a:A): A = null.asInstanceOf[A]");
  }
}
