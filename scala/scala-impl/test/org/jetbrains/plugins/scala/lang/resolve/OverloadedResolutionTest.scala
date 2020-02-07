package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.junit.Assert._

/**
  * @author Nikolay.Tropin
  */
class OverloadedResolutionTest extends ScalaResolveTestCase {

  override def folderPath: String = s"${super.folderPath}resolve/overloadedResolution"

  def testSCL7890(): Unit = doTest()

  def testSCL12277_1(): Unit = doTest()

  def testSCL12277_2(): Unit = doTest()

  def testSCL12120(): Unit = doTest()

  //SCL-15381
  def testByNameParameter(): Unit = doTest()

  def testSCL15408(): Unit = doTest()

  private def doTest() = {
    findReferenceAtCaret() match {
      case ref: ScReference =>
        val variants = ref.multiResolveScala(false)
        assertTrue(s"Single resolve expected, was: ${variants.length}", variants.length == 1)
    }
  }

}
