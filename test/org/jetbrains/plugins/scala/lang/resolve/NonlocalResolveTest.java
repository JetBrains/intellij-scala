package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDeclaration;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;
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

  public void testHigherKind() throws Exception {
    PsiReference ref = configureByFile("nonlocal/higherkind.scala");
    assertTrue(ref.resolve() instanceof ScTypeDefinition);
  }
}