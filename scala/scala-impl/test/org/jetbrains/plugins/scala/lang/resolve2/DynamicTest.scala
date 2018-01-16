package org.jetbrains.plugins.scala
package lang.resolve2
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.junit.Assert

/**
 * Pavel.Fatin, 02.02.2010
 */

class DynamicTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "dynamic/"
  }

  def testApplyDynamic() { doTest() }
  def testApplyDynamicNoMethod() { doTest() }
  def testApplyDynamicOrdinaryType() { doTest() }
  def testApplyDynamicWrongSygnature() { doTest() }
  def testSelectDynamicPostfix() { doTest() }

  override def doEachTest(reference: ScReferenceElement, options: Parameters): Unit = {
    super.doEachTest(reference, options)
    reference.bind().foreach { result =>
      Assert.assertEquals(result.nameArgForDynamic, Some(reference.refName))
    }
  }
}