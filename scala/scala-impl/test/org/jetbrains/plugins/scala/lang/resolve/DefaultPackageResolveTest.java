package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiReference;

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.03.2009
 */
public class DefaultPackageResolveTest extends ScalaResolveTestCase {
  public String folderPath() {
    return super.folderPath() + "resolve/defaultPackage/";
  }

  @Override
  public String sourceRootPath() {
    return folderPath();
  }

  public void testScalaToJava() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() == null);
  }

  public void testScalaToScala() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() == null);
  }

  public void testScalaToScript() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() == null);
  }

  public void testDefaultScalaToJava() throws Exception {
    PsiReference ref = findReferenceAtCaret();
    assertTrue(ref.resolve() != null);
  }
}
