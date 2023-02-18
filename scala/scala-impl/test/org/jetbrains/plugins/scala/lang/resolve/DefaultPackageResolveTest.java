package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiReference;

public class DefaultPackageResolveTest extends ScalaResolveTestCase {
  @Override
  public String folderPath() {
    return super.folderPath() + "resolve/defaultPackage/";
  }

  @Override
  public String sourceRootPath() {
    return folderPath();
  }

  public void testScalaToJava() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertNull(ref.resolve());
  }

  public void testScalaToScala() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertNull(ref.resolve());
  }

  public void testScalaToScript() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertNull(ref.resolve());
  }

  public void testDefaultScalaToJava() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertNotNull(ref.resolve());
  }
}
