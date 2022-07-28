package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition;

public class FunctionsTypeParam2ResolveTest extends ScalaResolveTestCase {
  @Override
  public String folderPath() {
    return super.folderPath() + "resolve/functions/typeParam2/";
  }

  public void testtp2() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScFunctionDefinition);
    assertEquals("def gul(i:Int) : Int = i", resolved.getText());
  }
}
