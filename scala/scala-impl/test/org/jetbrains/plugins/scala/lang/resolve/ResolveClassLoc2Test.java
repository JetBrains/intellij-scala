package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait;

import java.nio.file.Path;

public class ResolveClassLoc2Test extends ScalaResolveTestCase {

  @Override
  public Path folderPath() {
    return super.folderPath().resolve("resolve").resolve("class").resolve("loc2");
  }

  public void testloc2() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTrait);
    assertEquals("MyTrait", ((ScTrait) resolved).qualifiedName());
  }
}
