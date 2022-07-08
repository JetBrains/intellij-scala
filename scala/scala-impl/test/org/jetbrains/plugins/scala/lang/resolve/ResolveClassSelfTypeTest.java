package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait;

public class ResolveClassSelfTypeTest extends ScalaResolveTestCase {
  @Override
  public String folderPath() {
    return super.folderPath() + "resolve/class/selftype/";
  }

  public void testselftype1() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTrait);
    final ScTrait trait = (ScTrait) resolved;
    assertEquals(trait.getName(), "Inner");
  }

  public void testselftype2() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTrait);
    final ScTrait trait = (ScTrait) resolved;
    assertEquals(trait.getName(), "Inner");
  }

  public void testselftype3() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTrait);
    final ScTrait trait = (ScTrait) resolved;
    assertEquals(trait.getName(), "Inner");
  }

  public void testselftype4() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTrait);
    final ScTrait trait = (ScTrait) resolved;
    assertEquals(trait.getName(), "Inner");
  }
}
