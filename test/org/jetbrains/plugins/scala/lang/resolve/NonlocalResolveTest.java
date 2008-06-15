package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiMethod;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDeclaration;
import org.jetbrains.plugins.scala.util.TestUtils;

/**
 * @author ven
 */
public class NonlocalResolveTest extends ScalaResolveTestCase{

  protected String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/resolve/nonlocal/";
  }

  public void testTypeDecl() throws Exception {
    PsiReference ref = configureByFile("nonlocal/typedecl.scala");
    assertTrue(ref.resolve() instanceof ScTypeAliasDeclaration);
  }

  public void testSubstitutor1() throws Exception {
    PsiReference ref = configureByFile("nonlocal/substitutor1.scala");
    assertTrue(ref.resolve() instanceof PsiMethod);
  }
}