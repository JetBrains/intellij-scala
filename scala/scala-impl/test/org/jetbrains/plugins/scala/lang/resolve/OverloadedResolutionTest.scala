package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.junit.Assert._

/**
  * @author Nikolay.Tropin
  */
class OverloadedResolutionTest extends ScalaResolveTestCase {

  override def folderPath(): String = s"${super.folderPath()}resolve/overloadedResolution"

  override def rootPath(): String = folderPath()

  def testSCL7890(): Unit = doTest()

  def testSCL12277_1(): Unit = doTest()

  def testSCL12277_2(): Unit = doTest()

  def testSCL12120(): Unit = doTest()

  private def doTest() = {
    findReferenceAtCaret() match {
      case ref: ScReferenceElement =>
        val variants = ref.multiResolve(false)
        assertTrue(s"Single resolve expected, was: ${variants.length}", variants.length == 1)
    }
  }

}
