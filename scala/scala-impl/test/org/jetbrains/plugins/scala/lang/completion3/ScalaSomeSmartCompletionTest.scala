package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType.SMART
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.junit.Test

class ScalaSomeSmartCompletionTest extends ScalaCompletionTestBase {

  @Test
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
    invocationCount = 2,
    completionType = SMART
  )

  @Test
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
    invocationCount = 2,
    completionType = SMART
  )

  @Test
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
    invocationCount = 2,
    completionType = SMART
  )

  @Test
  def testSomeSmart4(): Unit = {
    val ty =
      if (version.isScala3) ": { def z: A }"
      else ""
    doCompletionTest(
      fileText =
        s"""
           |class TUI {
           |  class A
           |  def foo(x: Option[A]) = 1
           |  val ko$ty = new {def z: A = new A}
           |  val u: Option[A] = ko.$CARET
           |}
        """.stripMargin,
      resultText =
        s"""
           |class TUI {
           |  class A
           |  def foo(x: Option[A]) = 1
           |  val ko$ty = new {def z: A = new A}
           |  val u: Option[A] = Some(ko.z)$CARET
           |}
        """.stripMargin,
      item = "z",
      invocationCount = 2,
      completionType = SMART
    )
  }

  @Test
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
    invocationCount = 2,
    completionType = SMART
  )

  @Test
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
    invocationCount = 2,
    completionType = SMART
  )

  @Test
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
    invocationCount = 2,
    completionType = SMART
  )
}
