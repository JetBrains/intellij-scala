package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.annotator.Message.Error
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaVersion}
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingTestBase

class ScExportStmtAnnotatorTest extends ScalaHighlightingTestBase{
  override def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3

  def testOkExportInExtension(): Unit =
    assertNoErrors(
      """extension (x: Int)
        |  def str = ""
        |  export str.*
        |""".stripMargin
    )

  def testExportInExtensionTargetsValue(): Unit =
    assertErrors(
      """extension (x: Int)
        |  export x.*
        |""".stripMargin,
      Error("x", ScalaBundle.message("export.qualifier.not.parameterless.companion.method", "x"))
    )

  def testExportInExtensionTargetsParameterizedMethod_type_params(): Unit =
    assertErrors(
      """extension (x: Int)
        |  def str[T] = ""
        |  export str.*
        |""".stripMargin,
      Error("str", ScalaBundle.message("export.qualifier.not.parameterless.companion.method", "str"))
    )

  def testExportInExtensionTargetsParameterizedMethod_params_0(): Unit =
    assertErrors(
      """extension (x: Int)
        |  def str() = ""
        |  export str.*
        |""".stripMargin,
      Error("str", ScalaBundle.message("export.qualifier.not.parameterless.companion.method", "str"))
    )

  def testExportInExtensionTargetsParameterizedMethod_params_1(): Unit =
    assertErrors(
      """extension (x: Int)
        |  def str(a: Int) = ""
        |  export str.*
        |""".stripMargin,
      Error("str", ScalaBundle.message("export.qualifier.not.parameterless.companion.method", "str"))
    )

  def testExportInExtensionTargetsOuterMethod(): Unit =
    assertErrors(
      """def str = ""
        |
        |extension (x: Int)
        |  export str.*
        |""".stripMargin,
      Error("str", ScalaBundle.message("export.qualifier.not.parameterless.companion.method", "str"))
    )
}
