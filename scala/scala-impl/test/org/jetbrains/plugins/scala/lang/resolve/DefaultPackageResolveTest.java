package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiReference;

import java.nio.file.Path;

public class DefaultPackageResolveTest extends ScalaResolveTestCase {
  @Override

  public Path folderPath() {
    return super.folderPath().resolve("resolve").resolve("defaultPackage");
  }

  @Override
  public Path sourceRootPath() {
    return folderPath();
  }

  public void testScalaToJava() {
    PsiReference ref = findReferenceAtCaret();
    assertNull(ref.resolve());
  }

  public void testScalaToScala() {
    PsiReference ref = findReferenceAtCaret();
    assertNull(ref.resolve());
  }

  public void testScalaToScript() {
    PsiReference ref = findReferenceAtCaret();
    assertNull(ref.resolve());
  }

  public void testDefaultScalaToJava() {
    PsiReference ref = findReferenceAtCaret();
    assertNotNull(ref.resolve());
  }
}
