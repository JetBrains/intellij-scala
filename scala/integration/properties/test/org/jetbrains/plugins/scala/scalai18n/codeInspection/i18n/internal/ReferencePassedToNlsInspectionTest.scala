package org.jetbrains.plugins.scala
package scalai18n
package codeInspection
package i18n
package internal

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.{InspectionBundle, ScalaInspectionTestBase}

class ReferencePassedToNlsInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ReferencePassedToNlsInspection]

  override protected val description = InspectionBundle.message("internal.expression.without.nls.passed.to.nls")

  override protected def createTestText(text: String): String =
    s"""
       |object org {
       |  object jetbrains {
       |    object annotations {
       |      class Nls extends scala.annotation.StaticAnnotation
       |    }
       |  }
       |}
       |import org.jetbrains.annotations.Nls
       |
       |@Nls
       |val nls: String = null
       |def toNls(@Nls arg: String): Unit = ()
       |
       |$text
       |""".stripMargin

  def test_simple_val_ref(): Unit =
    checkTextHasError(
      s"""
         |val ref = "blub"
         |toNls(${START}ref$END)
         |""".stripMargin)

  def test_simple_def_ref(): Unit =
    checkTextHasError(
      s"""
         |def ref = "blub"
         |toNls(${START}ref$END)
         |""".stripMargin)

  def test_annotated_ref(): Unit =
    checkTextHasNoErrors(
      s"""
         |@Nls
         |def ref = null
         |toNls(ref)
         |""".stripMargin)

  def test_inner_def(): Unit =
    checkTextHasNoErrors(
      s"""
         |def ref = nls
         |toNls(ref)
         |""".stripMargin)

  def test_inner_val(): Unit =
    checkTextHasNoErrors(
      s"""
         |val ref = nls
         |toNls(ref)
         |""".stripMargin)

  def test_inner_var(): Unit =
    checkTextHasError(
      s"""
         |var ref = nls
         |toNls(${START}ref$END)
         |""".stripMargin)

  def test_recursion_pos(): Unit =
    checkTextHasNoErrors(
      s"""
         |def ref = {
         |  if (null) nls
         |  else ref
         |}
         |toNls(ref)
         |""".stripMargin)

  def test_recursion_neg(): Unit =
    checkTextHasError(
      s"""
         |def ref = {
         |  if (null) "blub"
         |  else ref
         |}
         |toNls(${START}ref$END)
         |""".stripMargin)
}
