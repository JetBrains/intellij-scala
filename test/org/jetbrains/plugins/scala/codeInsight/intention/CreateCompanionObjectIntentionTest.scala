package org.jetbrains.plugins.scala.codeInsight.intention

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
  * mattfowler
  * 5/21/2016
  */
class CreateCompanionObjectIntentionTest extends ScalaIntentionTestBase {
  val familyName = CreateCompanionObjectIntention.getFamilyName

  def testShouldCreateCompanion(): Unit = {
    val text =
      s"""
         |class B<caret>ar {}
       """.stripMargin

    val expected =
      s"""
         |class B<caret>ar {}
         |
         |object Bar {
         |
         |}
       """.stripMargin

    doTest(text, expected)
  }

  def testShouldNotShowIfAlreadyHasCompanion(): Unit =
    checkIntentionIsNotAvailable(
      s"""
         |class F<caret>oo { }
         |
         |object Foo { }
       """.stripMargin)

  def testShouldNotShowIfAlreadyHasCompanionAbove(): Unit =
    checkIntentionIsNotAvailable(
      s"""
         |object Foo { }
         |class F<caret>oo { }
       """.stripMargin)

  def testShouldNotShowOnCompanion(): Unit =
    checkIntentionIsNotAvailable(
      s"""
         |class Foo { }
         |
         |object F<caret>oo { }
       """.stripMargin)

}
