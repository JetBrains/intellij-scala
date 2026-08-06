package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait;

import java.nio.file.Path;

public class ResolveClassSelfTypeTest extends ScalaResolveTestCase {
  @Override
  public Path folderPath() {
    return super.folderPath().resolve("resolve").resolve("class").resolve("selftype");
  }

  public void testselftype1() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTrait);
    final ScTrait trait = (ScTrait) resolved;
    assertEquals("Inner", trait.getName());
  }

  public void testselftype2() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTrait);
    final ScTrait trait = (ScTrait) resolved;
    assertEquals("Inner", trait.getName());
  }

  public void testselftype3() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTrait);
    final ScTrait trait = (ScTrait) resolved;
    assertEquals("Inner", trait.getName());
  }

  public void testselftype4() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTrait);
    final ScTrait trait = (ScTrait) resolved;
    assertEquals("Inner", trait.getName());
  }
}
