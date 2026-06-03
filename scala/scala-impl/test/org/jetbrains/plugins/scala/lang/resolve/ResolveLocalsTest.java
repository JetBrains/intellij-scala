package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction;
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter;

import java.nio.file.Path;

public class ResolveLocalsTest extends ScalaResolveTestCase{

  @Override
  public Path folderPath() {
    return super.folderPath().resolve("resolve").resolve("local");
  }

  public void testlocal1() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScReferencePattern);
    assertEquals("aaa", ((ScReferencePattern) resolved).name());
  }

  public void testScalaKeyword() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof ScParameter);
  }

  public void testgilles() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScFunction);
    assertEquals("iii", ((ScFunction) resolved).name());
  }

  public void testconstrParam() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScParameter);
  }

  public void testdefInAnonymous() {
    PsiReference ref = findReferenceAtCaret();
    assertNotNull(ref.resolve());
  }

  public void testInfixType() {
    PsiReference ref = findReferenceAtCaret();
    assertNotNull(ref.resolve());
  }
}
