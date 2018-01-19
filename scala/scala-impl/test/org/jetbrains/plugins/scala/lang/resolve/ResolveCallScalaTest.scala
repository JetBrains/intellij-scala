package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.junit.Assert._

/**
  * @author Alefas
  * @since 01/07/16
  */
class ResolveCallScalaTest extends ScalaResolveTestCase {
  override def folderPath: String = super.folderPath + "resolve/call"

  def doTest(): Unit = {
    findReferenceAtCaret() match {
      case ref: ScReferenceElement =>
        val variants = ref.multiResolveScala(false)
        assertTrue(s"Single resolve expected, was: ${variants.length}", variants.length == 1)
    }
  }

  def testSCL10091(): Unit = doTest()
}
