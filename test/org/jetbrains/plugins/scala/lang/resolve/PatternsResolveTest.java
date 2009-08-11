package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern;
import org.jetbrains.plugins.scala.util.TestUtils;

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.08.2009
 */
public class PatternsResolveTest extends ScalaResolveTestCase{

  public String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/resolve/";
  }

  public void testScalaCollectionFullyQualified() throws Exception {
    PsiReference ref = configureByFile("patterns/ExtractorPattern.scala");
    assertTrue(ref.resolve() instanceof ScBindingPattern);
  }
}

