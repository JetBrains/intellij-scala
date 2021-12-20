package org.jetbrains.plugins.scala.lang.optimize.generated

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.optimize.OptimizeImportsTestBase

class OptimizeImportsTest_Scala3 extends OptimizeImportsTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0

  def testExportClausesAreNotModified(): Unit = {
    doTest(
      """class A {
        |  def foo = ???
        |}
        |
        |export AWrapper.*
        |export AWrapper.given
        |export AWrapper.{given}
        |export AWrapper.{given String}
        |
        |object AWrapper {
        |  val b = new A
        |  export b.*
        |}""".stripMargin
    )
  }

}
