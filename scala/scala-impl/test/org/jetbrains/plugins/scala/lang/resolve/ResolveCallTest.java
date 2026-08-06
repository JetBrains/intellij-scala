package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScSelfInvocation;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition;
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter;
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction;
import scala.Option;

import java.nio.file.Path;

public class ResolveCallTest extends ScalaResolveTestCase {
  @Override
  public Path folderPath() {
    return super.folderPath().resolve("resolve").resolve("call");
  }

  public void testSelfConstructorCall() {
    PsiElement elementAt = getFile().findElementAt(getEditor().getCaretModel().getOffset());
    ScSelfInvocation selfInvocation = PsiTreeUtil.getTopmostParentOfType(elementAt, ScSelfInvocation.class);
    Option<PsiElement> bind = selfInvocation.bind();
    assertTrue(bind.isDefined());
    assertTrue(bind.get() instanceof ScPrimaryConstructor);
  }

  public void testEmptySelfConstructorCall() {
    PsiElement elementAt = getFile().findElementAt(getEditor().getCaretModel().getOffset());
    ScSelfInvocation selfInvocation = PsiTreeUtil.getTopmostParentOfType(elementAt, ScSelfInvocation.class);
    Option<PsiElement> bind = selfInvocation.bind();
    assertTrue(bind.isDefined());
    assertTrue(bind.get() instanceof ScPrimaryConstructor);
  }

  public void testisInstanceOf() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof ScSyntheticFunction);
  }

  public void testAssignmentCall() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof ScFunction);
  }

  public void testImplicitConversionOfPrivate() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof ScFunction); //this is not Java PsiMethod, which has private access
  }

  public void testobjectApply() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScFunction);
    assertEquals("apply", ((ScFunction) resolved).getName());
  }

  public void testObjectGenericApply() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScFunction);
    assertEquals("apply", ((ScFunction) resolved).getName());
  }

  public void testrefPattern() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScFunction);
    assertEquals("foo", ((ScFunction) resolved).getName());
  }

  public void testSuperConstructorInvocation() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScFunction);
    assertEquals("c", ((ScFunction) resolved).containingClass().getName());
  }

  public void testNamingParam() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScParameter);
  }

  public void testsimpleCallParensOmitted() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScFunctionDefinition);
  }

  public void testSCL3458() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScFunctionDefinition);
  }
}
