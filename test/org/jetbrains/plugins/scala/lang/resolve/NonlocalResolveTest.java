package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDeclaration;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern;
import org.jetbrains.plugins.scala.util.TestUtils;

/**
 * @author ven
 */
public class NonlocalResolveTest extends ScalaResolveTestCase{

  protected String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/resolve/";
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

  public void testCompoundTypes() throws Exception {
    PsiReference ref = configureByFile("nonlocal/compoundtypes.scala");
    assertTrue(ref.resolve() instanceof ScTypeAlias);
  }

  public void testValsAsPatterns() throws Exception {
    PsiReference ref = configureByFile("nonlocal/valsaspatterns.scala");
    assertTrue(ref.resolve() instanceof ScPattern);
  }

  public void testTraitSuperTypes() throws Exception {
    PsiReference ref = configureByFile("nonlocal/traitsupertypes.scala");
    assertTrue(ref.resolve() instanceof ScFunction);
  }

  public void testTypeAliases() throws Exception {
    PsiReference ref = configureByFile("nonlocal/typealiases.scala");
    assertTrue(ref.resolve() instanceof PsiMethod);
  }

  public void testJavaGenerics() throws Exception {
    PsiReference ref = configureByFile("nonlocal/javaGenerics.scala");
    assertTrue(ref.resolve() instanceof PsiMethod);
  }
}