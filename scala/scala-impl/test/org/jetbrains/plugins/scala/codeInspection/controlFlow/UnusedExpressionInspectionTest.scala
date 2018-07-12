package org.jetbrains.plugins.scala
package codeInspection
package controlFlow

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

/**
 * Nikolay.Tropin
 * 2014-09-23
 */
class UnusedExpressionInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ScalaUnusedExpressionInspection]

  override protected val description: String =
    InspectionBundle.message("unused.expression.no.side.effects")

  def testLiteral(): Unit = {
    val text = s"""def foo(): Int = {
                 |    if (true) return 1
                 |    else ${START}2$END
                 |
                 |    0
                 |}"""
    checkTextHasError(text)
  }

  def testTuple(): Unit = {
    val text = s"""def foo(): Unit = {
                 |    var x = 0
                 |    $START(0, 2)$END
                 |    0
                 |}"""
    checkTextHasError(text)
  }

  def testReference(): Unit = {
    val text = s"""def foo(): Unit = {
                 |    $START(0, 2)._1$END
                 |    0
                 |}"""
    checkTextHasError(text)
  }

  def testReferenceToVal(): Unit = {
    val text = s"""def foo(): Unit = {
                 |  val a = 1
                 |  ${START}a$END
                 |  0
                 |}"""
    checkTextHasError(text)
  }

  def testTypedAndParenthesized(): Unit = {
    val text = s"""def foo(): Unit = {
                   |  val s = "aaa"
                   |  $START(s: String).substring(0)$END
                   |  0
                   |}"""
    checkTextHasError(text)
  }

  def testReferenceToByNameParam(): Unit = {
    val text = s"""def foo(i: => Int): Int = {
                 |  ${START}i$END
                 |  0
                 |}"""
    checkTextHasNoErrors(text)
  }

  def testStringBuffer(): Unit = {
    val text = s"""def foo(): Int = {
                 |  val b = new StringBuffer()
                 |  ${START}b.append("a")$END
                 |  0
                 |}"""
    checkTextHasNoErrors(text)
  }

  def testObjectMethodWithSideEffects(): Unit = {
    val text = s"""def foo(): Int = {
                 |  $START"1".wait()$END
                 |  0
                 |}"""
    checkTextHasNoErrors(text)
  }

  def testImmutableCollection(): Unit = {
    val text = s"""def foo(): Int = {
                  |  0 match {
                  |    case 0 => ${START}List(1)$END
                  |    case 1 =>
                  |  }
                  |  1
                  |}"""
    checkTextHasError(text)
  }

  def testImmutableCollection2(): Unit = {
    val text = s"""def foo(): Int = {
                  |  0 match {
                  |    case 0 => ${START}List(1).dropRight(2)$END
                  |    case 1 =>
                  |  }
                  |  1
                  |}"""
    checkTextHasError(text)
  }

  def testImmutableCollection3(): Unit = {
    val text = s"""def foo(): Int = {
                  |  val f = (i: Int) => i + 1
                  |  0 match {
                  |    case 0 => ${START}List(1).foreach(f)$END
                  |    case 1 =>
                  |  }
                  |  1
                  |}"""
    checkTextHasNoErrors(text)
  }

  def testImmutableCollection4(): Unit = {
    val text = s"""def foo(): Int = {
                   |  0 match {
                   |    case 0 => ${START}List(1).map(_ + 2)$END
                   |    case 1 =>
                   |  }
                   |  1
                   |}"""
    checkTextHasError(text)
  }

  def testThisReference(): Unit = {
    val text = s"""class A {
                  |  val x = 1
                  |
                  |  def foo(): Int = {
                  |    0 match {
                  |      case 0 => ${START}this.x$END
                  |      case 1 =>
                  |    }
                  |    1
                  |  }
                  |}"""
    checkTextHasError(text)
  }

  def testFunctionalParam(): Unit = {
    val text =
      s"""def foo(f: Int => Unit): Unit = {
         |  ${START}List(1) foreach f$END
         |}
       """
    checkTextHasNoErrors(text)
  }

  def testStringMethod(): Unit = {
    val text = s"""def foo(): Int = {
                    |  0 match {
                    |    case 0 => $START"1".substring(1)$END
                    |    case 1 =>
                    |  }
                    |  1
                    |}"""
    checkTextHasError(text)
  }

  def testNoForAssignment(): Unit = {
    val text = s"""def foo(): Int = {
                 |    var x = 0
                 |    ${START}x += 1$END
                 |    0
                 |}"""
    checkTextHasNoErrors(text)
  }

  def testNoForAssignment2(): Unit = {
    val text = s"""def foo(): Int = {
                 |    var x = 0
                 |    ${START}x = 1$END
                 |    0
                 |}"""
    checkTextHasNoErrors(text)
  }

  def testUnitFunction(): Unit = {
    val text = s"""def foo(): Unit = {
                 |  var z = 0
                 |  if (true) z = 1
                 |  else ${START}2$END
                 |}"""
    checkTextHasError(text)
  }

  def testUnitFunction2(): Unit = {
    val text = s"""def foo(): Unit = {
                 |  $START"1".wait()$END
                 |}"""
    checkTextHasNoErrors(text)
  }

  def testImplicitClass(): Unit = {
    val text =
      s"""implicit class StringOps(val s: String) {
         |  def print(): Unit = println(s)
         |}
         |
         |def foo(): Unit = {
         |  $START"1".print()$END
         |}"""
    checkTextHasNoErrors(text)
  }

  def testImplicitFunction(): Unit = {
    val text =
      s"""implicit def stringToInteger(s: String): Integer = Integer.valueOf(s.length)
        |
        |def foo(): Unit = {
        |  $START"1".intValue()$END
        |}"""
    checkTextHasNoErrors(text)
  }

  def testObject(): Unit = {
    val text =
      """
        |class Test {
        |  def foo(): Unit = {
        |    A
        |    println("world")
        |  }
        |}
        |
        |object A {
        |  println("hello")
        |}
        |
      """
    checkTextHasNoErrors(text)
  }

  def testConstructorCall(): Unit = {
    val text =
      """
        |class Test {
        |  def foo(): Unit = {
        |    new A
        |    println("world")
        |  }
        |}
        |
        |class A {
        |  println("hello")
        |}
        |
      """
    checkTextHasNoErrors(text)
  }

  def testUnderscoreApply(): Unit = {
    val text =
      """
        |object ToDo {
        |  var todo: Option[() => Unit] = None
        |
        |  def doIt() = todo.foreach(_ ())
        |  def doItToo() = todo.foreach(_.apply())
        |  todo.foreach(_())
        |}
      """
    checkTextHasNoErrors(text)
  }

  def testStringGetChars(): Unit = {
    val text =
      """
        |def foo(): Unit = {
        |  val target = Array.empty[Char]
        |  "abcde".getChars(0, 1, target, 0)
        |}
      """
    checkTextHasNoErrors(text)
  }
}

class UnusedExpressionThrowsInspectionTest extends ScalaInspectionTestBase {
  override protected val description = InspectionBundle.message("unused.expression.throws")

  override protected val classOfInspection = classOf[ScalaUnusedExpressionInspection]

  def testUnsafeGet(): Unit = {
    checkTextHasError(
      s"""
        |def foo: Unit = {
        |  import scala.util.Try
        |  val tr = Try {
        |    throw new IllegalStateException()
        |  }
        |  ${START}tr.get$END
        |}
      """.stripMargin)
  }

  def testUnsafeHead(): Unit = {
    checkTextHasError(
      s"""
        |def foo: Unit = {
        |  val list: List[String] = Nil
        |  ${START}list.head$END
        |}
      """.stripMargin)
  }

  def testUnsafeGetProjection(): Unit = {
    checkTextHasError(
      s"""
        |def foo: Unit = {
        |  val left = Left("a")
        |  ${START}Left("a").right.get$END
        |}
      """.stripMargin)
  }
}
