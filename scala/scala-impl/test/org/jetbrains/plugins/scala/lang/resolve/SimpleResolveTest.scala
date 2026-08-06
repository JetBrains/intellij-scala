package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.junit.Assert._

import java.nio.file.Path

abstract class SimpleResolveTest(dirName: String) extends ScalaResolveTestCase {

  override def folderPath: Path = super.folderPath / "resolve" / "simple" / dirName

  override def sourceRootPath: Path = folderPath

  def doTest(): Unit = {
    findReferenceAtCaret() match {
      case ref: ScReference =>
        val variants = ref.multiResolveScala(false)
        assertTrue(s"Single resolve expected, was: ${variants.length}", variants.length == 1)
    }
  }
}
