package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition;

import java.nio.file.Path;

public class FunctionsTypeParam1ResolveTest extends ScalaResolveTestCase {
  @Override
  public Path folderPath() {
    return super.folderPath().resolve("resolve").resolve("functions").resolve("typeParam1");
  }

  public void testtp1() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScFunctionDefinition);
    assertEquals("def gul[A](a:A): A = null.asInstanceOf[A]", resolved.getText());
  }
}
