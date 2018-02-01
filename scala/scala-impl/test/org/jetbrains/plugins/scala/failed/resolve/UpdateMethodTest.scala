package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class UpdateMethodTest extends FailedResolveTest("updateMethod") {
  def testSCL5739() = doTest()

  override protected def additionalAsserts(variants: Array[ScalaResolveResult], ref: ScReferenceElement): Boolean = {
    val elementFile = variants(0).getElement.getContainingFile
    val res = elementFile == ref.getContainingFile
    if (!res) println(s"Should resolve to the same file, was: ${elementFile.getName}")
    res
  }
}
