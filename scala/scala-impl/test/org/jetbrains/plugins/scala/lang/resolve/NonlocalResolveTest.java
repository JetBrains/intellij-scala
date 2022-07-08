package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference;
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDeclaration;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;

public class NonlocalResolveTest extends ScalaResolveTestCase{

  @Override
  public String folderPath() {
    return super.folderPath() + "resolve/nonlocal/";
  }

  public void testBeanProperty() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof ScPrimaryConstructor);
  }

  public void testArrayBufferAdd() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScFunction);
    ScFunction function = (ScFunction) resolved;
    assertEquals(function.containingClass().getName(), "ArrayBuffer");
  }

  public void testMathSimple() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof PsiClass);
    assertEquals("java.lang.Math", ((PsiClass) resolved).getQualifiedName());
  }

  public void testCompoundTypesOverriding() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof ScFunction);
    ScFunction fun = (ScFunction) resolved;
    ScTemplateDefinition clazz = fun.containingClass();
    assertTrue(clazz.getName().equals("C"));    
  }

  public void testMathImported() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof PsiClass);
    assertEquals("java.lang.Math", ((PsiClass) resolved).getQualifiedName());
  }

  public void testtypedecl() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof ScTypeAliasDeclaration);
  }

  public void testexistential() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTypeAlias);
  }

  public void testimportFromObject() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement element = ref.resolve();
    assertTrue(element instanceof ScFunction && ((ScFunction) element).getName().equals("apply"));
  }

  public void testsubstitutor1() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof PsiMethod);
  }

  public void testhigherkind() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof ScTypeDefinition);
  }

  public void testhigherkind1() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof ScFunction);
  }

  public void testcompoundtypes() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof ScTypeAlias);
  }

  public void testvalsaspatterns() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof ScPattern);
  }

  public void testtraitsupertypes() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof ScFunction);
  }

  public void testtypealiases() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof PsiMethod);
  }

  public void testjavaGenerics() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof PsiMethod);
  }

  public void testself() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    final PsiElement t = ref.resolve();
    assertTrue(t instanceof ScFunction);
    assertEquals(((ScFunction) t).getName(), "ccc");
  }

  public void testselfTypeShadow() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    final PsiElement t = ref.resolve();
    assertTrue(t instanceof ScClass);
    assertEquals(((ScClass) t).qualifiedName(), "scala.Symbol");
  }

  public void testsubstAliasBound() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof PsiMethod);
  }

  public void testrecursiveInvocation() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof PsiMethod);
  }

  public void testrecursivePattern() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertNull(ref.resolve());
  }

  public void testbaseClassParam() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertNotNull(ref.resolve());
  }

  public void testlub1() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertNotNull(ref.resolve());
  }

  public void testNoShadowing() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertNull(ref.resolve());
  }

  public void testGood() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertNotNull(ref.resolve());
  }
  
  public void testSCL3666() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    if (ref instanceof ScReference) {
      ScReference refElement = (ScReference) ref;
      assertNotNull(ref.resolve());
      assertTrue(refElement.bind().get().isApplicable(false));
    }
  }

  public void testTwoImports() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertNull(ref.resolve());
  }
}