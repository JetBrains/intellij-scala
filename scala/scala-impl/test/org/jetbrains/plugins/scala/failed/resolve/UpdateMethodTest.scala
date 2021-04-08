package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
  * @author Nikolay.Tropin
  */
class UpdateMethodTest extends FailedResolveTest("updateMethod") {
  def testSCL5739(): Unit = doTest()

  override protected def additionalAsserts(variants: Array[ScalaResolveResult], ref: ScReference): Boolean = {
    val elementFile = variants(0).getElement.getContainingFile
    val res = elementFile == ref.getContainingFile
    if (!res) println(s"Should resolve to the same file, was: ${elementFile.getName}")
    res
  }
}
