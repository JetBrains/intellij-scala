package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition;

import java.nio.file.Path;

public class PatternsResolveTest extends ScalaResolveTestCase {

  @Override

  public Path folderPath() {
    return super.folderPath().resolve("resolve").resolve("patterns");
  }

  public void testExtractorPattern() {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof ScFunctionDefinition);
  }
}
