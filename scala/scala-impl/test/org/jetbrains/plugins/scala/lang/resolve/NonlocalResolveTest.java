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

import java.nio.file.Path;

public class NonlocalResolveTest extends ScalaResolveTestCase{

  @Override
  public Path folderPath() {
    return super.folderPath().resolve("resolve").resolve("nonlocal");
  }

  public void testBeanProperty() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof ScPrimaryConstructor);
  }

  public void testArrayBufferAdd() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScFunction);
    ScFunction function = (ScFunction) resolved;
    assertEquals("ArrayBuffer", function.containingClass().getName());
  }

  public void testMathSimple() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof PsiClass);
    assertEquals("java.lang.Math", ((PsiClass) resolved).getQualifiedName());
  }

  public void testCompoundTypesOverriding() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof ScFunction);
    ScFunction fun = (ScFunction) resolved;
    ScTemplateDefinition clazz = fun.containingClass();
    assertEquals("C", clazz.getName());
  }

  public void testMathImported() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof PsiClass);
    assertEquals("java.lang.Math", ((PsiClass) resolved).getQualifiedName());
  }

  public void testtypedecl() {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof ScTypeAliasDeclaration);
  }

  public void testexistential() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTypeAlias);
  }

  public void testimportFromObject() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement element = ref.resolve();
    assertTrue(element instanceof ScFunction && ((ScFunction) element).getName().equals("apply"));
  }

  public void testsubstitutor1() {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof PsiMethod);
  }

  public void testhigherkind() {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof ScTypeDefinition);
  }

  public void testhigherkind1() {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof ScFunction);
  }

  public void testcompoundtypes() {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof ScTypeAlias);
  }

  public void testvalsaspatterns() {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof ScPattern);
  }

  public void testtraitsupertypes() {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof ScFunction);
  }

  public void testtypealiases() {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof PsiMethod);
  }

  public void testjavaGenerics() {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof PsiMethod);
  }

  public void testself() {
    PsiReference ref = findReferenceAtCaret();
    final PsiElement t = ref.resolve();
    assertTrue(t instanceof ScFunction);
    assertEquals("ccc", ((ScFunction) t).getName());
  }

  public void testselfTypeShadow() {
    PsiReference ref = findReferenceAtCaret();
    final PsiElement t = ref.resolve();
    assertTrue(t instanceof ScClass);
    assertEquals("scala.Symbol", ((ScClass) t).qualifiedName());
  }

  public void testsubstAliasBound() {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof PsiMethod);
  }

  public void testrecursiveInvocation() {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() instanceof PsiMethod);
  }

  public void testrecursivePattern() {
    PsiReference ref = findReferenceAtCaret();
    assertNull(ref.resolve());
  }

  public void testbaseClassParam() {
    PsiReference ref = findReferenceAtCaret();
    assertNotNull(ref.resolve());
  }

  public void testlub1() {
    PsiReference ref = findReferenceAtCaret();
    assertNotNull(ref.resolve());
  }

  public void testNoShadowing() {
    PsiReference ref = findReferenceAtCaret();
    assertNull(ref.resolve());
  }

  public void testGood() {
    PsiReference ref = findReferenceAtCaret();
    assertNotNull(ref.resolve());
  }
  
  public void testSCL3666() {
    PsiReference ref = findReferenceAtCaret();
    if (ref instanceof ScReference refElement) {
      assertNotNull(ref.resolve());
      assertTrue(refElement.bind().get().isApplicable(false));
    }
  }

  public void testTwoImports() {
    PsiReference ref = findReferenceAtCaret();
    assertNull(ref.resolve());
  }
}
