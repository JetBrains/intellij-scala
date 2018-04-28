package org.jetbrains.plugins.scala
package codeInsight
package intention
package matcher

import com.intellij.testFramework.EditorTestUtil
import junit.framework.ComparisonFailure
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
  * Nikolay.Tropin
  * 22-May-17
  */
class CreateCaseClausesIntentionTest extends ScalaIntentionTestBase {

  import CreateCaseClausesIntention._
  import EditorTestUtil.{CARET_TAG => C}

  override def familyName: String = FamilyName

  def testSealedTrait(): Unit = doTest(
    s"""sealed trait X
       |
       |class A(s: String) extends X
       |
       |case class B(s: String) extends X
       |
       |(_: X) match {$C}
       """.stripMargin,
    s"""sealed trait X
       |
       |class A(s: String) extends X
       |
       |case class B(s: String) extends X
       |
       |(_: X) match {$C
       |  case _: A =>
       |  case B(s) =>
       |}
       """.stripMargin
  )

  def testJavaEnum(): Unit = doTest(
    s"""import java.nio.file.FileVisitResult
       |
       |(_: FileVisitResult) match {$C}
       """.stripMargin,
    s"""import java.nio.file.FileVisitResult
       |
       |(_: FileVisitResult) match {$C
       |  case FileVisitResult.CONTINUE =>
       |  case FileVisitResult.TERMINATE =>
       |  case FileVisitResult.SKIP_SUBTREE =>
       |  case FileVisitResult.SKIP_SIBLINGS =>
       |}
       """.stripMargin
  )

  def testFromScalaPackage(): Unit = {
    val text =
      s"""(_: List[String]) match {$C}
       """.stripMargin

    val resultText =
      s"""(_: List[String]) match {$C
         |  case Nil =>
         |  case ::(head, tl) =>
         |}
         """.stripMargin

    try {
      doTest(text, resultText)
    } catch {
      case _: ComparisonFailure =>
        doTest(text, resultText.replace("head", "hd")) //parameter name depends on scala version
    }
  }

  def testVarargs(): Unit = doTest(
    s"""sealed trait Foo
       |
       |case class Bar(foos: Foo*) extends Foo
       |
       |(_: Foo) match {$C}
       """.stripMargin,
    s"""sealed trait Foo
       |
       |case class Bar(foos: Foo*) extends Foo
       |
       |(_: Foo) match {$C
       |  case Bar(foos@_*) =>
       |}
       """.stripMargin
  )

}
