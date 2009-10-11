package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDeclaration;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;
import org.jetbrains.plugins.scala.util.TestUtils;

/**
 * @author ven
 */
public class NonlocalResolveTest extends ScalaResolveTestCase{

  public String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/resolve/";
  }

  public void testTypeDecl() throws Exception {
    PsiReference ref = configureByFile("nonlocal/typedecl.scala");
    assertTrue(ref.resolve() instanceof ScTypeAliasDeclaration);
  }

  public void testCyclicExistential() throws Exception {
    PsiReference ref = configureByFile("nonlocal/existential.scala");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTypeAlias);
  }

  public void testImportfromObject() throws Exception {
    PsiReference ref = configureByFile("nonlocal/importFromObject.scala");
    PsiElement element = ref.resolve();
    assertTrue(element instanceof ScClass && ((ScClass) element).isCase());
  }

  public void testSubstitutor1() throws Exception {
    PsiReference ref = configureByFile("nonlocal/substitutor1.scala");
    assertTrue(ref.resolve() instanceof PsiMethod);
  }

  public void testHigherKind() throws Exception {
    PsiReference ref = configureByFile("nonlocal/higherkind.scala");
    assertTrue(ref.resolve() instanceof ScTypeDefinition);
  }

  public void testHigherKind1() throws Exception {
    PsiReference ref = configureByFile("nonlocal/higherkind1.scala");
    assertTrue(ref.resolve() instanceof ScFunction);
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

  public void testSelfType() throws Exception {
    PsiReference ref = configureByFile("nonlocal/self.scala");
    final PsiElement t = ref.resolve();
    assertTrue(t instanceof ScFunction);
    assertEquals(((ScFunction) t).getName(), "ccc");
  }

  public void testSelfTypeShadow() throws Exception {
    PsiReference ref = configureByFile("nonlocal/selfTypeShadow.scala");
    final PsiElement t = ref.resolve();
    assertTrue(t instanceof ScTrait);
    assertEquals(((ScTrait) t).getQualifiedName(), "Symbols.Symbol");
  }

  public void testSubstAliasBound() throws Exception {
    PsiReference ref = configureByFile("nonlocal/substAliasBound.scala");
    assertTrue(ref.resolve() instanceof PsiMethod);
  }

  public void testRecursiveInvocation() throws Exception {
    PsiReference ref = configureByFile("nonlocal/recursiveInvocation.scala");
    assertTrue(ref.resolve() instanceof PsiMethod);
  }

  public void testRecursivePattern() throws Exception {
    PsiReference ref = configureByFile("nonlocal/recursivePattern.scala");
    assertNull(ref.resolve());
  }

  public void testBaseClassParam() throws Exception {
    PsiReference ref = configureByFile("nonlocal/baseClassParam.scala");
    assertNotNull(ref.resolve());
  }

  public void testLUB1() throws Exception {
    PsiReference ref = configureByFile("nonlocal/lub1.scala");
    assertNotNull(ref.resolve());
  }
}