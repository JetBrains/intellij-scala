package org.jetbrains.plugins.scala.codeInspection.syntacticSimplification

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}

class PostfixUnaryOperationInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection : Class[_ <: LocalInspectionTool] = classOf[PostfixUnaryOperationInspection]
  override protected val description = ScalaInspectionBundle.message("displayname.postfix.unary.operation")

  private val hint = ScalaInspectionBundle.message("unary.operation.can.use.prefix.notation")

  def testPostfixUnaryFunctionReference(): Unit = {
    val original =
      s"""import scala.language.postfixOps
         |
         |object A {
         |  def unary_~(n: Int) = ~n
         |  def f(): Int = 5 ${START}unary_~$END
         |}
      """.stripMargin

    val result =
      s"""import scala.language.postfixOps
         |
         |object A {
         |  def unary_~(n: Int) = ~n
         |  def f(): Int = ~5
         |}
      """.stripMargin

    checkTextHasError(original)
    testQuickFix(original, result, hint)
  }

  def testFunctionReferenceWithQualifier(): Unit = {
    val original =
      s"""object A {
         |  def unary_~(n: Int) = ~n
         |  def f(): Seq[Int] = Seq(1, 2, 3).map(A.${START}unary_~$END)
         |}
      """.stripMargin

    val result =
      s"""object A {
         |  def unary_~(n: Int) = ~n
         |  def f(): Seq[Int] = Seq(1, 2, 3).map(~A)
         |}
      """.stripMargin

    checkTextHasError(original)
    testQuickFix(original, result, hint)
  }

  def testFunctionReferenceWithoutQualifier(): Unit = {
    val original =
      s"""object A {
         |  def unary_~(n: Int) = ~n
         |  def f(): Seq[Int] = Seq(1, 2, 3).map(unary_~)
         |}
      """.stripMargin

    checkTextHasNoErrors(original)
  }

  def testOnIntInsideUnderscoreLambda(): Unit = {
    val original =
      s"""object A {
         |  def f(): Seq[Int] = Seq(1, 2, 3).map(_.${START}unary_~$END)
         |}
      """.stripMargin

    val result =
      s"""object A {
         |  def f(): Seq[Int] = Seq(1, 2, 3).map(~_)
         |}
      """.stripMargin

    checkTextHasError(original)
    testQuickFix(original, result, hint)
  }

  def testOnClassInsideUnderscoreLambda(): Unit = {
    val original =
      s"""case class A(val n: Int) {
         |  def unary_~ : A = A(~n)
         |}
         |
         |object A {
         |  def f(): Seq[A] = Seq(A(1), A(2), A(3)).map(_.${START}unary_~$END)
         |}
      """.stripMargin

    val result =
      s"""case class A(val n: Int) {
         |  def unary_~ : A = A(~n)
         |}
         |
         |object A {
         |  def f(): Seq[A] = Seq(A(1), A(2), A(3)).map(~_)
         |}
      """.stripMargin

    checkTextHasError(original)
    testQuickFix(original, result, hint)
  }

  def testOnClassWithUnaryOps(): Unit = {
    val original =
      s"""case class A(val n: Int) {
         |  def unary_~ : A = A(~n)
         |}
         |
         |object A {
         |  def f(): A = A(5).${START}unary_~$END
         |}
      """.stripMargin

    val result =
      s"""case class A(val n: Int) {
         |  def unary_~ : A = A(~n)
         |}
         |
         |object A {
         |  def f(): A = ~A(5)
         |}
      """.stripMargin

    checkTextHasError(original)
    testQuickFix(original, result, hint)
  }

  def testOnSimpleBoolean(): Unit = {
    val original =
      s"""object A {
         |  def f(): Boolean = true.${START}unary_!$END
         |}
      """.stripMargin

    val result =
      s"""object A {
         |  def f(): Boolean = !true
         |}
      """.stripMargin

    checkTextHasError(original)
    testQuickFix(original, result, hint)
  }

  def `testOnUnary+OnInt`(): Unit = {
    val original =
      s"""object A {
         |  def f(): Int = 1.${START}unary_+$END
         |}
      """.stripMargin

    val result =
      s"""object A {
         |  def f(): Int = +1
         |}
      """.stripMargin

    checkTextHasError(original)
    testQuickFix(original, result, hint)
  }

  def `testOnUnary-OnInt`(): Unit = {
    val original =
      s"""object A {
         |  def f(): Int = 1.${START}unary_-$END
         |}
      """.stripMargin

    val result =
      s"""object A {
         |  def f(): Int = -1
         |}
      """.stripMargin

    checkTextHasError(original)
    testQuickFix(original, result, hint)
  }

  def `testOnUnary~OnInt`(): Unit = {
    val original =
      s"""object A {
         |  def f(): Int = 1.${START}unary_~$END
         |}
      """.stripMargin

    val result =
      s"""object A {
         |  def f(): Int = ~1
         |}
      """.stripMargin

    checkTextHasError(original)
    testQuickFix(original, result, hint)
  }
}
