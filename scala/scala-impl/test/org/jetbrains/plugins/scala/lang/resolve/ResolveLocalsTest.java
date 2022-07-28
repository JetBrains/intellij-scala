package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction;
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter;

public class ResolveLocalsTest extends ScalaResolveTestCase{

  @Override
  public String folderPath() {
    return super.folderPath() + "resolve/local/";
  }

  public void testlocal1() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScReferencePattern);
    assertEquals(((ScReferencePattern) resolved).name(), "aaa");
  }

  public void testScalaKeyword() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof ScParameter);
  }

  public void testgilles() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScFunction);
    assertEquals(((ScFunction) resolved).name(), "iii");
  }

  public void testconstrParam() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScParameter);
  }

  public void testdefInAnonymous() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertNotNull(ref.resolve());
  }

  public void testInfixType() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertNotNull(ref.resolve());
  }
}
