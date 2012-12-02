package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement;
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition;
import org.jetbrains.plugins.scala.util.TestUtils;

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.08.2009
 */
public class PatternsResolveTest extends ScalaResolveTestCase {

  public String folderPath() {
    return super.folderPath() + "resolve/patterns/";
  }

  public void testExtractorPattern() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof ScFunctionDefinition);
  }
}

