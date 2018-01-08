package org.jetbrains.plugins.scala
package lang.resolve

import com.intellij.lang.properties.IProperty
import com.intellij.psi.{PsiElement, PsiReference}
import org.junit.Assert

/**
 * Nikolay.Tropin
 * 2014-09-26
 */
class ResolvePropertyKeyTest extends ScalaResolveTestCase {
  override def folderPath: String = {
    super.folderPath + "resolve/propertyKey/"
  }

  protected override def rootPath: String = {
    super.folderPath + "resolve/propertyKey/"
  }

  def testMain() {
    val ref: PsiReference = findReferenceAtCaret
    val psiElement: PsiElement = ref.resolve
    Assert.assertTrue(psiElement.isInstanceOf[IProperty])
  }

  // TODO Use a more reliable way to locate module test data.
  override def getTestDataPath =
    super.getTestDataPath.replace("scala-impl/testData", "integration/properties/testResources")
}
