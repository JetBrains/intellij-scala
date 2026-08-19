package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.junit.Assert._

import java.nio.file.Path

abstract class OverloadedResolutionTestBase extends ScalaResolveTestCase {

  override def folderPath: Path = super.folderPath / "resolve" / "overloadedResolution"

  protected def doTest(): Unit = {
    findReferenceAtCaret() match {
      case ref: ScReference =>
        val variants = ref.multiResolveScala(false)
        assertTrue(s"Single resolve expected, was: ${variants.length}", variants.length == 1)
    }
  }
}

class OverloadedResolutionTest extends OverloadedResolutionTestBase {

  def testSCL7890(): Unit = doTest()

  def testSCL12277_1(): Unit = doTest()

  def testSCL12277_2(): Unit = doTest()

  def testSCL12120(): Unit = doTest()

  //SCL-15381
  def testByNameParameter(): Unit = doTest()

  def testSCL15408(): Unit = doTest()

  def testScalaPluginCachedMethods(): Unit = doTest()
}

/**
 * Scala 2.11 cannot infer the parameter type of a function literal which is passed to an overloaded method
 * (scalac reports "missing parameter type"), so the argument type cannot single out an alternative either.
 * Since Scala 2.12 the parameter types of all alternatives are lub-ed to build the expected type
 * (see `canLubParamTpes` in [[org.jetbrains.plugins.scala.lang.psi.impl.expr.ExpectedTypesImpl]]).
 */
class OverloadedResolutionTest_since_2_12 extends OverloadedResolutionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_2_12

  def testSCL12052(): Unit = doTest()
}
