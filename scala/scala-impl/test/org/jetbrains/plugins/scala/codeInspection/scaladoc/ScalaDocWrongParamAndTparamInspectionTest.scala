package org.jetbrains.plugins.scala.codeInspection.scaladoc

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

class ScalaDocWrongParamAndTparamInspectionTest extends ScalaInspectionTestBase {

  override protected val classOfInspection = classOf[ScalaDocUnknownParameterInspection]
  override protected val description = "@param and @tparams tags aren't allowed there"

  def testDeleteOnVal(): Unit = {
    testQuickFix(
      """/**
        | * @param
        | */
        |val x = ???""".stripMargin,
      """/**
        | * */
        |val x = ???""".stripMargin,
      "Delete tag"
    )
  }

  def testNoWarning(): Unit =
    checkTextHasNoErrors(
      """/**
        | * For ScalaTest, Spec2, uTest
        | *
        | * @param yyy description yyyyyyy
        | */
        |@deprecated()
        |class YyyClass(yyy: String)""".stripMargin
    )

  def testNoWarning_AnnotationBeforeDoc(): Unit =
    checkTextHasNoErrors(
      """@deprecated()
        |/**
        | * For ScalaTest, Spec2, uTest
        | *
        | * @param xxx description xxxxxx
        | */
        |class XxxClass(xxx: String)""".stripMargin
    )
}