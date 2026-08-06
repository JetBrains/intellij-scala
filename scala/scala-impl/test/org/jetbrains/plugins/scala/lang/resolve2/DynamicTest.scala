package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.junit.Assert

import java.nio.file.Path

class DynamicTest extends ResolveTestBase {

  override def folderPath: Path = super.folderPath / "dynamic"

  def testApplyDynamic(): Unit = doTest()
  def testApplyDynamicNoMethod(): Unit = doTest()
  def testApplyDynamicOrdinaryType(): Unit = doTest()
  def testApplyDynamicWrongSignature(): Unit = doTest()
  def testSelectDynamicPostfix(): Unit = doTest()

  def testSelectDynamicInType(): Unit = doTest()
  def testSelectDynamicInTypeMacro(): Unit = doTest()

  override protected def doEachTest(reference: ScReference, referenceIndex: Int, expectedResolveResult: ExpectedResolveResult): Unit = {
    super.doEachTest(reference, referenceIndex, expectedResolveResult)

    reference.bind().foreach { result =>
      Assert.assertEquals(result.nameArgForDynamic, Some(reference.refName))
    }
  }
}
