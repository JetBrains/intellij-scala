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

public class ResolveCallTest extends ScalaResolveTestCase {
  @Override
  public String folderPath() {
    return super.folderPath() + "/resolve/call/";
  }

  public void testSelfConstructorCall() throws Exception {
    PsiElement elementAt = getFile().findElementAt(getEditor().getCaretModel().getOffset());
    ScSelfInvocation selfInvocation = PsiTreeUtil.getTopmostParentOfType(elementAt, ScSelfInvocation.class);
    Option<PsiElement> bind = selfInvocation.bind();
    assertTrue(bind.isDefined());
    assertTrue(bind.get() instanceof ScPrimaryConstructor);
  }

  public void testEmptySelfConstructorCall() throws Exception {
    PsiElement elementAt = getFile().findElementAt(getEditor().getCaretModel().getOffset());
    ScSelfInvocation selfInvocation = PsiTreeUtil.getTopmostParentOfType(elementAt, ScSelfInvocation.class);
    Option<PsiElement> bind = selfInvocation.bind();
    assertTrue(bind.isDefined());
    assertTrue(bind.get() instanceof ScPrimaryConstructor);
  }

  public void testisInstanceOf() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof ScSyntheticFunction);
  }

  public void testAssignmentCall() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof ScFunction);
  }

  public void testImplicitConversionOfPrivate() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof ScFunction); //this is not Java PsiMethod, which has private access
  }

  public void testobjectApply() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScFunction);
    assertEquals("apply", ((ScFunction) resolved).getName());
  }

  public void testObjectGenericApply() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScFunction);
    assertEquals("apply", ((ScFunction) resolved).getName());
  }

  public void testrefPattern() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScFunction);
    assertEquals("foo", ((ScFunction) resolved).getName());
  }

  public void testSuperConstructorInvocation() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScFunction);
    assertEquals("c", ((ScFunction) resolved).containingClass().getName());
  }

  public void testNamingParam() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScParameter);
  }

  public void testsimpleCallParensOmitted() throws Exception {
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
