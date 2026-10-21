package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.junit.Assert._

import java.nio.file.Path

abstract class ResolveImplicitConversionBase extends ScalaResolveTestCase {
  override def folderPath: Path = super.folderPath / "resolve" / "implicitConversion"

  def doTest(): Unit = {
    findReferenceAtCaret() match {
      case ref: ScReference =>
        val variants = ref.multiResolveScala(false)
        assertTrue(s"Single resolve expected, was: ${variants.length}", variants.length == 1)
    }
  }

}

class ResolveImplicitConversion extends ResolveImplicitConversionBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version > LatestScalaVersions.Scala_2_12

  def testScl4968(): Unit = doTest()

  def testSCL8757(): Unit = doTest()

  def testSCL8660(): Unit = doTest()

  def testSCL10670(): Unit = doTest()

  def testSCL10549(): Unit = doTest()

  def testSCL9224(): Unit = doTest()

  def testSCL12251(): Unit = doTest()

  def testSCL13686(): Unit = doTest()
}

class ResolveImplicitConversion_2_12 extends ResolveImplicitConversionBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12

  def testScl7974(): Unit = doTest()
}
