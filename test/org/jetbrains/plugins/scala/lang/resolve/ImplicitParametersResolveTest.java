package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;
import org.jetbrains.plugins.scala.util.TestUtils;

public class ImplicitParametersResolveTest extends ScalaResolveTestCase {

  public String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/resolve/";
  }

  public void testLocalValAsImplicitParam() throws Exception {
    PsiReference ref = configureByFile("implicitParameter/localValAsImplicitParam.scala");
    assertTrue(ref.resolve() instanceof ScFunctionDefinition);
  }
}