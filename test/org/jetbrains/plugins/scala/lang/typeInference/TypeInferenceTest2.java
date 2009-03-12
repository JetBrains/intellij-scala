package org.jetbrains.plugins.scala.lang.typeInference;

import com.intellij.testFramework.ResolveTestCase;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter;
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveTestCase;

/**
 * @author ilyas
 */
public class TypeInferenceTest2 extends ScalaResolveTestCase {
  @Override
  protected String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/typeInference/";
  }

  public void testFunParameter() throws Exception {
    PsiReference ref = configureByFile("expected/param/Param.scala");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScParameter);
    String string = (((ScParameter) resolved)).calcType().toString();
    assertTrue(string.equals("String"));
  }
}
