package org.jetbrains.plugins.scala
package codeInsight
package intentions
package companionObject

/**
  * mattfowler
  * 5/21/2016
  */
class CreateCompanionObjectIntentionTest extends ScalaIntentionTestBase {
  override val familyName = ScalaBundle.message("family.name.create.companion.object")

  def testShouldCreateCompanion(): Unit = {
    val text =
      s"""
         |class B<caret>ar {}
       """.stripMargin

    val expected =
      s"""
         |class Bar {}
         |
         |object Bar {
         |<caret>
         |}
       """.stripMargin

    doTest(text, expected)
  }

  def testShouldCreateCompanionForTrait(): Unit = {
    val text =
      s"""
         |trait B<caret>ar {}
       """.stripMargin

    val expected =
      s"""
         |trait Bar {}
         |
         |object Bar {
         |<caret>
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
