package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.codeInsight.completion.CompletionType.SMART
import com.intellij.testFramework.EditorTestUtil

/**
  * @author Alexander Podkhalyuzin
  */
class ScalaSomeSmartCompletionTest extends ScalaCodeInsightTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}

  def testSomeSmart1(): Unit = doCompletionTest(
    fileText =
      s"""
         |class TUI {
         |  class A
         |  def foo(x: Option[A]) = 1
         |  val z = new A
         |  foo($CARET)
         |}
      """.stripMargin,
    resultText =
      s"""
         |class TUI {
         |  class A
         |  def foo(x: Option[A]) = 1
         |  val z = new A
         |  foo(Some(z)$CARET)
         |}
      """.stripMargin,
    item = "z",
    time = 2,
    completionType = SMART
  )

  def testSomeSmart2(): Unit = doCompletionTest(
    fileText =
      s"""
         |class TUI {
         |  class A
         |  def foo(x: Option[A]) = 1
         |  val z = new A
         |  foo($CARET)
         |}
        """.stripMargin,
    resultText =
      s"""
         |class TUI {
         |  class A
         |  def foo(x: Option[A]) = 1
         |  val z = new A
         |  foo(Some(z),$CARET)
         |}
         """.stripMargin,
    item = "z",
    char = ',',
    time = 2,
    completionType = SMART
  )

  def testSomeSmart3(): Unit = doCompletionTest(
    fileText =
      s"""
         |class TUI {
         |  class A
         |  def foo(x: Option[A]) = 1
         |  val z = new A
         |  val u: Option[A] = $CARET
         |}
        """.stripMargin,
    resultText =
      s"""
         |class TUI {
         |  class A
         |  def foo(x: Option[A]) = 1
         |  val z = new A
         |  val u: Option[A] = Some(z)$CARET
         |}
        """.stripMargin,
    item = "z",
    time = 2,
    completionType = SMART
  )

  def testSomeSmart4(): Unit = doCompletionTest(
    fileText =
      s"""
         |class TUI {
         |  class A
         |  def foo(x: Option[A]) = 1
         |  val ko = new {def z: A = new A}
         |  val u: Option[A] = ko.$CARET
         |}
        """.stripMargin,
    resultText =
      s"""
         |class TUI {
         |  class A
         |  def foo(x: Option[A]) = 1
         |  val ko = new {def z: A = new A}
         |  val u: Option[A] = Some(ko.z)$CARET
         |}
        """.stripMargin,
    item = "z",
    time = 2,
    completionType = SMART
  )

  def testSomeSmart5(): Unit = doCompletionTest(
    fileText =
      s"""
         |class TUI {
         |  class A
         |  class B {def z(x: Int): A = new A}
         |  val ko = new B
         |  val u: Option[A] = ko.$CARET
         |}
         """.stripMargin,
    resultText =
      s"""
         |class TUI {
         |  class A
         |  class B {def z(x: Int): A = new A}
         |  val ko = new B
         |  val u: Option[A] = Some(ko.z($CARET))
         |}
        """.stripMargin,
    item = "z",
    time = 2,
    completionType = SMART
  )

  def testOuterThis(): Unit = doCompletionTest(
    fileText =
      s"""
         |class TT {
         |  class GG {
         |    val al: Option[TT] = $CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class TT {
         |  class GG {
         |    val al: Option[TT] = Some(TT.this)$CARET
         |  }
         |}
      """.stripMargin,
    item = "TT.this",
    time = 2,
    completionType = SMART
  )

  def testSomeScalaEnum(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Scala extends Enumeration {type Scala = Value; val aaa, bbb, ccc = Value}
         |class A {
         |  val x: Option[Scala.Scala] = a$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |object Scala extends Enumeration {type Scala = Value; val aaa, bbb, ccc = Value}
         |class A {
         |  val x: Option[Scala.Scala] = Some(Scala.aaa)$CARET
         |}
      """.stripMargin,
    item = "aaa",
    time = 2,
    completionType = SMART
  )
}