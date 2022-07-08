package org.jetbrains.plugins.scala
package lang.resolve2
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.junit.Assert

class DynamicTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "dynamic/"
  }

  def testApplyDynamic(): Unit = { doTest() }
  def testApplyDynamicNoMethod(): Unit = { doTest() }
  def testApplyDynamicOrdinaryType(): Unit = { doTest() }
  def testApplyDynamicWrongSignature(): Unit = { doTest() }
  def testSelectDynamicPostfix(): Unit = { doTest() }

  def testSelectDynamicInType(): Unit = doTest()
  def testSelectDynamicInTypeMacro(): Unit = doTest()

  override def doEachTest(reference: ScReference, options: Parameters): Unit = {
    super.doEachTest(reference, options)
    reference.bind().foreach { result =>
      Assert.assertEquals(result.nameArgForDynamic, Some(reference.refName))
    }
  }
}