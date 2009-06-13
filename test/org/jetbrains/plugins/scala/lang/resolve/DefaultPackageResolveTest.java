package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.testFramework.ResolveTestCase;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDeclaration;

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.03.2009
 */
public class DefaultPackageResolveTest extends ScalaResolveTestCase {
  public String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/resolve/";
  }

  public void testScalaToJava() throws Exception {
    PsiReference ref = configureByFile("defaultPackage/ScalaToJava.scala");
    assertTrue(ref.resolve() == null);
  }

  public void testScalaToScala() throws Exception {
    PsiReference ref = configureByFile("defaultPackage/ScalaToScala.scala");
    assertTrue(ref.resolve() != null);
  }

  public void testScalaToScript() throws Exception {
    PsiReference ref = configureByFile("defaultPackage/ScalaToScript.scala");
    assertTrue(ref.resolve() == null);
  }

  public void testDefaultScalaToJava() throws Exception {
    PsiReference ref = configureByFile("defaultPackage/DefaultScalaToJava.scala");
    assertTrue(ref.resolve() != null);
  }
}
