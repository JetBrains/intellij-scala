package org.jetbrains.plugins.scala.codeInspection.specs2

import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}

class BuiltinMatcherExistsInspectionTest extends ScalaInspectionTestBase {

  protected val annotation: String = ScalaInspectionBundle.message("specs2.use.builtin.matcher")
  private val hint = ScalaInspectionBundle.message("specs2.builtin.matcher.alternative.exists")
  override protected val classOfInspection: Class[BuiltinMatcherExistsInspection] = classOf[BuiltinMatcherExistsInspection]

  //OperationOnCollectionInspectionBase
  def testMustBeSomeSimplification(): Unit = {
    Seq("be_===", "be_==", "beEqualTo", "equalTo", "beTypedEqualTo", "typedEqualTo").foreach { matcher =>
      val code =
        s"""
           |expr must $START$matcher(Some("123"))$END
        """.stripMargin
      val expected =
        """
          |expr must beSome("123")
        """.stripMargin

      checkTextHasError(code)

      testQuickFix(code, expected, hint)
    }
  }

  def testMustBeNoneSimplification(): Unit = {
    Seq("be_===", "be_==", "beEqualTo", "equalTo", "beTypedEqualTo", "typedEqualTo").foreach { matcher =>
      val code =
        s"""
           |expr must $START$matcher(None)$END
        """.stripMargin
      val expected =
        """
          |expr must beNone
        """.stripMargin

      checkTextHasError(code)

      testQuickFix(code, expected, hint)
    }
  }

  def testMustBeLeftSimplification(): Unit = {
    Seq("be_===", "be_==", "beEqualTo", "equalTo", "beTypedEqualTo", "typedEqualTo").foreach { matcher =>
      val code =
        s"""
           |expr must $START$matcher(Left("123"))$END
        """.stripMargin
      val expected =
        """
          |expr must beLeft("123")
        """.stripMargin

      checkTextHasError(code)

      testQuickFix(code, expected, hint)
    }
  }

  def testMustBeRightSimplification(): Unit = {
    Seq("be_===", "be_==", "beEqualTo", "equalTo", "beTypedEqualTo", "typedEqualTo").foreach { matcher =>
      val code =
        s"""
           |expr must $START$matcher(Right("123"))$END
        """.stripMargin
      val expected =
        """
          |expr must beRight("123")
        """.stripMargin

      checkTextHasError(code)

      testQuickFix(code, expected, hint)
    }
  }

  def testMustEqualBeSomeSimplification(): Unit = {
    Seq("must_===", "must_==", "mustEqual").foreach { matcher =>
      val code =
        s"""
           |${START}expr $matcher Some("123")$END
        """.stripMargin
      val expected =
        """
          |expr must beSome("123")
        """.stripMargin

      checkTextHasError(code)

      testQuickFix(code, expected, hint)
    }
  }

  def testMustEqualBeNoneSimplification(): Unit = {
    Seq("must_===", "must_==", "mustEqual").foreach { matcher =>
      val code =
        s"""
           |${START}expr $matcher None$END
        """.stripMargin
      val expected =
        """
          |expr must beNone
        """.stripMargin

      checkTextHasError(code)

      testQuickFix(code, expected, hint)
    }
  }

  def testMustEqualBeEitherSimplification(): Unit = {
    Seq("must_===", "must_==", "mustEqual").foreach { matcher =>
      Seq("Left", "Right").foreach { either =>
        val code =
          s"""
             |${START}expr $matcher $either("123")$END
          """.stripMargin
        val expected =
          s"""
             |expr must be$either("123")
          """.stripMargin

        checkTextHasError(code)

        testQuickFix(code, expected, hint)
      }
    }
  }

  override protected val description: String = ScalaInspectionBundle.message("specs2.use.builtin.matcher")
}
