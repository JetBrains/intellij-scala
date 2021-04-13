package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.failed.resolve.FailableResolveTest
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

class UpdateMethodTest extends FailableResolveTest("updateMethod") {

  override protected def shouldPass = true

  def testSCL5739(): Unit = doTest()

  override protected def additionalAsserts(variants: Array[ScalaResolveResult], ref: ScReference): Boolean = {
    val elementFile = variants(0).getElement.getContainingFile
    val res = elementFile == ref.getContainingFile
    if (!res) println(s"Should resolve to the same file, was: ${elementFile.getName}")
    res
  }
}