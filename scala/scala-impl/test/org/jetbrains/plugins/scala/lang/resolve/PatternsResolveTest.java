package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition;

public class PatternsResolveTest extends ScalaResolveTestCase {

  @Override
  public String folderPath() {
    return super.folderPath() + "resolve/patterns/";
  }

  public void testExtractorPattern() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof ScFunctionDefinition);
  }
}

