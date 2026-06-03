package org.jetbrains.plugins.scala
package codeInspection
package internal

import com.intellij.codeInspection.LocalInspectionTool

class InstanceOfInspectionTest extends ScalaInspectionTestBase {

  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[InstanceOfInspection]

  override protected val description: String = ScalaInspectionBundle.message("replace.with.is")

  def test_related_instanceOf(): Unit = {
    checkTextHasError(
      s"""
         |class X
         |class Y extends X
         |
         |val b: X = null
         |b.${START}isInstanceOf${END}[Y]
         |""".stripMargin
    )

    val text =
      s"""
         |class X
         |class Y extends X
         |
         |val b: X = null
         |b.isI${CARET}nstanceOf[Y]
         |""".stripMargin
    val result =
      s"""
         |class X
         |class Y extends X
         |
         |val b: X = null
         |b.is[Y]
         |""".stripMargin
    testQuickFix(text, result, description)
  }


  def test_unrelated_instanceOf(): Unit =
    checkTextHasNoErrors(
      s"""
         |class X
         |class Y
         |
         |val b: X = null
         |b.isInstanceOf[Y]
         |""".stripMargin
    )
}
