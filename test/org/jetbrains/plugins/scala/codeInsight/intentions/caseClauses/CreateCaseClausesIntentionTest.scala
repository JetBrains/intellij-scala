package org.jetbrains.plugins.scala.codeInsight.intentions.caseClauses


import junit.framework.ComparisonFailure
import org.jetbrains.plugins.scala.codeInsight.intention.matcher.CreateCaseClausesIntention
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
  * Nikolay.Tropin
  * 22-May-17
  */
class CreateCaseClausesIntentionTest extends ScalaIntentionTestBase {
  override def familyName: String = new CreateCaseClausesIntention().getFamilyName

  def testSealedTrait(): Unit = {
    val text =
      """sealed trait X
        |
        |class A(s: String) extends X
        |
        |case class B(s: String) extends X
        |
        |val x: X = ???
        |x match {<caret>}""".stripMargin

    val result =
      """sealed trait X
        |
        |class A(s: String) extends X
        |
        |case class B(s: String) extends X
        |
        |val x: X = ???
        |x match {<caret>
        |  case _: A =>
        |  case B(s) =>
        |}
      """.stripMargin

    doTest(text, result)
  }

  def testJavaEnum(): Unit = {
    val text =
      """
        |import java.nio.file.FileVisitResult
        |
        |val x: FileVisitResult = ???
        |x match {<caret>}
      """.stripMargin

    val result =
      """
        |import java.nio.file.FileVisitResult
        |
        |val x: FileVisitResult = ???
        |x match {<caret>
        |  case FileVisitResult.CONTINUE =>
        |  case FileVisitResult.TERMINATE =>
        |  case FileVisitResult.SKIP_SUBTREE =>
        |  case FileVisitResult.SKIP_SIBLINGS =>
        |}
      """.stripMargin

    doTest(text, result)
  }

  def testFromScalaPackage(): Unit = {
    val text =
      """
        |val list: List[String] = ???
        |list match {<caret>}
      """.stripMargin

    val result =
      """
        |val list: List[String] = ???
        |list match {<caret>
        |  case Nil =>
        |  case ::(head, tl) =>
        |}
      """.stripMargin

    try {
      doTest(text, result)
    } catch {
      case c: ComparisonFailure =>
        doTest(text, result.replace("head", "hd")) //parameter name depends on scala version
    }
  }

}
