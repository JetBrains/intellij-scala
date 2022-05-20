package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

class MakeTypeMoreSpecificIntentionTest extends ScalaIntentionTestBase {
  import org.jetbrains.plugins.scala.ScalaBundle.message

  override def familyName = MakeTypeMoreSpecificIntention.FamilyName


  def test_make_var_type(): Unit = {
    doTest(
      s"""
        |var te${CARET}st: Any = 3
      """.stripMargin,
      """
        |var test: Int = 3
      """.stripMargin,
      Some(message("make.type.more.specific"))
    )

    checkIntentionIsNotAvailable("var test: Int = 3")
  }

  def test_make_val_type(): Unit = {
    doTest(
      s"""
         |val te${CARET}st: Any = 3
      """.stripMargin,
      """
        |val test: Int = 3
      """.stripMargin,
      Some(message("make.type.more.specific"))
    )

    checkIntentionIsNotAvailable("val test: Int = 3")
  }

  def test_make_def_type(): Unit = {
    doTest(
      s"""
         |def te${CARET}st(): Any = 3
      """.stripMargin,
      """
        |def test(): Int = 3
      """.stripMargin,
      Some(message("make.type.more.specific.fun"))
    )

    checkIntentionIsNotAvailable("def test(): Int = 3")
  }
}
