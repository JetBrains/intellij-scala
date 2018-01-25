package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.junit.Assert._

/**
  * @author Nikolay.Tropin
  */
abstract class SimpleResolveTest(dirName: String) extends ScalaResolveTestCase {

  override def folderPath(): String = s"${super.folderPath()}resolve/simple/$dirName"

  override def rootPath(): String = folderPath()

  def doTest(): Unit = {
    findReferenceAtCaret() match {
      case ref: ScReferenceElement =>
        val variants = ref.multiResolveScala(false)
        assertTrue(s"Single resolve expected, was: ${variants.length}", variants.length == 1)
    }
  }
}
