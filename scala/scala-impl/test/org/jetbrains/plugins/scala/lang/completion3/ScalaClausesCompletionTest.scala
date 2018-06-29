package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_12}
import org.jetbrains.plugins.scala.lang.completion.clauses.ExhaustiveMatchCompletionContributor

class ScalaClausesCompletionTest extends ScalaCodeInsightTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}
  import ScalaCodeInsightTestBase._

  override implicit val version: ScalaVersion = Scala_2_12

  def testSealedTrait(): Unit = doMatchCompletionTest(
    fileText =
      s"""sealed trait X
         |
         |class A(s: String) extends X
         |
         |case class B(s: String) extends X
         |
         |(_: X) $CARET
         """.stripMargin,
    resultText =
      s"""sealed trait X
         |
         |class A(s: String) extends X
         |
         |case class B(s: String) extends X
         |
         |(_: X) match {
         |  case a: A => $CARET
         |  case B(s) =>
         |}
         """.stripMargin
  )

  def testJavaEnum(): Unit = doMatchCompletionTest(
    fileText =
      s"""import java.nio.file.FileVisitResult
         |
         |(_: FileVisitResult) m$CARET
         """.stripMargin,
    resultText =
      s"""import java.nio.file.FileVisitResult
         |
         |(_: FileVisitResult) match {
         |  case FileVisitResult.CONTINUE => $CARET
         |  case FileVisitResult.TERMINATE =>
         |  case FileVisitResult.SKIP_SUBTREE =>
         |  case FileVisitResult.SKIP_SIBLINGS =>
         |}
         """.stripMargin
  )

  def testFromScalaPackage(): Unit = doMatchCompletionTest(
    fileText =
      s"""(_: List[String]) m$CARET
         """.stripMargin,
    resultText =
      s"""(_: List[String]) match {
         |  case Nil => $CARET
         |  case ::(head, tl) =>
         |}
           """.stripMargin
  )

  def testVarargs(): Unit = doMatchCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |case class Bar(foos: Foo*) extends Foo
         |
         |(_: Foo) m$CARET
         """.stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |case class Bar(foos: Foo*) extends Foo
         |
         |(_: Foo) match {
         |  case Bar(foos@_*) => $CARET
         |}
         """.stripMargin
  )

  private def doMatchCompletionTest(fileText: String, resultText: String): Unit = {
    import ExhaustiveMatchCompletionContributor.{ItemText, RendererTailText}
    super.doCompletionTest(fileText, resultText, DEFAULT_CHAR, DEFAULT_TIME, DEFAULT_COMPLETION_TYPE) {
      hasItemText(_, ItemText, ItemText, RendererTailText)
    }
  }
}
