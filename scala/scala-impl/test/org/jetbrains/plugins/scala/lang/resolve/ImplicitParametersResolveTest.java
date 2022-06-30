package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition;

public class ImplicitParametersResolveTest extends ScalaResolveTestCase {

  @Override
  public String folderPath() {
    return super.folderPath() + "resolve/implicitParameter";
  }

  public void testlocalValAsImplicitParam() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof ScFunctionDefinition);
  }
}