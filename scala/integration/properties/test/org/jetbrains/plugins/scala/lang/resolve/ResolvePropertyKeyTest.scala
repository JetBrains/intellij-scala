package org.jetbrains.plugins.scala
package lang.resolve

import com.intellij.lang.properties.IProperty
import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert

/**
 * Nikolay.Tropin
 * 2014-09-26
 */
class ResolvePropertyKeyTest extends ScalaResolveTestCase {
  override def folderPath: String = {
    testdataDir + "/resolve/propertyKey/"
  }

  protected override def rootPath: String = {
    testdataDir + "/resolve/propertyKey/"
  }

  def testMain() {
    val ref: PsiReference = findReferenceAtCaret
    val psiElement: PsiElement = ref.resolve
    Assert.assertTrue(psiElement.isInstanceOf[IProperty])
  }

  private def testdataDir = TestUtils.findTestdataDirForClass(this)
}
