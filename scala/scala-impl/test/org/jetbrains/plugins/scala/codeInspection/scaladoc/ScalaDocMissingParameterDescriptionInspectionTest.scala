package org.jetbrains.plugins.scala.codeInspection.scaladoc

import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}

class ScalaDocMissingParameterDescriptionInspectionTest extends ScalaInspectionTestBase {

  override protected val classOfInspection = classOf[ScalaDocMissingParameterDescriptionInspection]

  override protected val description = ScalaInspectionBundle.message("display.name.missing.parameter.description")

  def testWarningIfDescriptionIsMissing(): Unit =
    checkTextHasError(
      s"""/**
         | * @param ${START}x0$END
         | * @param x1 some text
         | * @param ${START}x2$END
         | */
         |class X(
         |  x0: Int,
         |  x1: Int,
         |  x2: Int,
         |)""".stripMargin
    )

  def testNoWarningsIfDescriptionIsPresent(): Unit =
    checkTextHasNoErrors(
      """/**
        | * @param x0 some text
        | * @param x1 $macro1
        | * @param x2 `markup text`
        | * @param x3 {{{example}}}
        | * @param x4 [[X]]
        | * @param x5 [[https://example.org]]
        | */
        |class X(
        |  x0: Int,
        |  x1: Int,
        |  x2: Int,
        |  x3: Int,
        |  x4: Int,
        |  x5: Int
        |)""".stripMargin
    )
}