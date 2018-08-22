package org.jetbrains.plugins.scala
package codeInspection
package controlFlow

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

/**
  * Nikolay.Tropin
  * 2014-09-23
  */
abstract class UnusedExpressionInspectionTestBase extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ScalaUnusedExpressionInspection]
}

class UnusedExpressionInspectionTest extends UnusedExpressionInspectionTestBase {

  override protected val description =
    InspectionBundle.message("unused.expression.no.side.effects")

  def testLiteral(): Unit = checkTextHasError {
    s"""def foo(): Int = {
       |    if (true) return 1
       |    else ${START}2$END
       |
                 |    0
       |}"""
  }

  def testTuple(): Unit = checkTextHasError {
    s"""def foo(): Unit = {
       |    var x = 0
       |    $START(0, 2)$END
       |    0
       |}"""
  }

  def testReference(): Unit = checkTextHasError {
    s"""def foo(): Unit = {
       |    $START(0, 2)._1$END
       |    0
       |}"""
  }

  def testReferenceToVal(): Unit = checkTextHasError {
    s"""def foo(): Unit = {
       |  val a = 1
       |  ${START}a$END
       |  0
       |}"""
  }

  def testTypedAndParenthesized(): Unit = checkTextHasError {
    s"""def foo(): Unit = {
       |  val s = "aaa"
       |  $START(s: String).substring(0)$END
       |  0
       |}"""
  }

  def testReferenceToByNameParam(): Unit = checkTextHasNoErrors {
    s"""def foo(i: => Int): Int = {
       |  ${START}i$END
       |  0
       |}"""
  }

  def testStringBuffer(): Unit = checkTextHasNoErrors {
    s"""def foo(): Int = {
       |  val b = new StringBuffer()
       |  ${START}b.append("a")$END
       |  0
       |}"""
  }

  def testObjectMethodWithSideEffects(): Unit = checkTextHasNoErrors {
    s"""def foo(): Int = {
       |  $START"1".wait()$END
       |  0
       |}"""
  }

  def testImmutableCollection(): Unit = checkTextHasError {
    s"""def foo(): Int = {
       |  0 match {
       |    case 0 => ${START}List(1)$END
       |    case 1 =>
       |  }
       |  1
       |}"""
  }

  def testImmutableCollection2(): Unit = checkTextHasError {
    s"""def foo(): Int = {
       |  0 match {
       |    case 0 => ${START}List(1).dropRight(2)$END
       |    case 1 =>
       |  }
       |  1
       |}"""
  }

  def testImmutableCollection3(): Unit = checkTextHasNoErrors {
    s"""def foo(): Int = {
       |  val f = (i: Int) => i + 1
       |  0 match {
       |    case 0 => ${START}List(1).foreach(f)$END
       |    case 1 =>
       |  }
       |  1
       |}"""
  }

  def testImmutableCollection4(): Unit = checkTextHasError {
    s"""def foo(): Int = {
       |  0 match {
       |    case 0 => ${START}List(1).map(_ + 2)$END
       |    case 1 =>
       |  }
       |  1
       |}"""
  }

  def testThisReference(): Unit = checkTextHasError {
    s"""class A {
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
  }

  def testFunctionalParam(): Unit = checkTextHasNoErrors {
    s"""def foo(f: Int => Unit): Unit = {
       |  ${START}List(1) foreach f$END
       |}
       """
  }

  def testStringMethod(): Unit = checkTextHasError {
    s"""def foo(): Int = {
       |  0 match {
       |    case 0 => $START"1".substring(1)$END
       |    case 1 =>
       |  }
       |  1
       |}"""
  }

  def testNoForAssignment(): Unit = checkTextHasNoErrors {
    s"""def foo(): Int = {
       |    var x = 0
       |    ${START}x += 1$END
       |    0
       |}"""
  }

  def testNoForAssignment2(): Unit = checkTextHasNoErrors {
    s"""def foo(): Int = {
       |    var x = 0
       |    ${START}x = 1$END
       |    0
       |}"""
  }

  def testUnitFunction(): Unit = checkTextHasError {
    s"""def foo(): Unit = {
       |  var z = 0
       |  if (true) z = 1
       |  else ${START}2$END
       |}"""
  }

  def testUnitFunction2(): Unit = checkTextHasNoErrors {
    s"""def foo(): Unit = {
       |  $START"1".wait()$END
       |}"""
  }

  def testImplicitClass(): Unit = checkTextHasNoErrors {
    s"""implicit class StringOps(val s: String) {
       |  def print(): Unit = println(s)
       |}
       |
       |def foo(): Unit = {
       |  $START"1".print()$END
       |}"""
  }

  def testImplicitFunction(): Unit = checkTextHasNoErrors {
    s"""implicit def stringToInteger(s: String): Integer = Integer.valueOf(s.length)
       |
       |def foo(): Unit = {
       |  $START"1".intValue()$END
       |}"""
  }

  def testObject(): Unit = checkTextHasNoErrors {
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
  }

  def testConstructorCall(): Unit = checkTextHasNoErrors {
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
  }

  def testUnderscoreApply(): Unit = checkTextHasNoErrors {
    """
      |object ToDo {
      |  var todo: Option[() => Unit] = None
      |
      |  def doIt() = todo.foreach(_ ())
      |  def doItToo() = todo.foreach(_.apply())
      |  todo.foreach(_())
      |}
    """
  }

  def testStringGetChars(): Unit = checkTextHasNoErrors {
    """
      |def foo(): Unit = {
      |  val target = Array.empty[Char]
      |  "abcde".getChars(0, 1, target, 0)
      |}
    """
  }
}

class UnusedExpressionThrowsInspectionTest extends UnusedExpressionInspectionTestBase {

  override protected val description =
    InspectionBundle.message("unused.expression.throws")

  def testUnsafeGet(): Unit = checkTextHasError {
    s"""
       |def foo: Unit = {
       |  import scala.util.Try
       |  val tr = Try {
       |    throw new IllegalStateException()
       |  }
       |  ${START}tr.get$END
       |}
      """
  }

  def testUnsafeHead(): Unit = checkTextHasError {
    s"""
       |def foo: Unit = {
       |  val list: List[String] = Nil
       |  ${START}list.head$END
       |}
      """
  }

  def testUnsafeGetProjection(): Unit = checkTextHasError {
    s"""
       |def foo: Unit = {
       |  val left = Left("a")
       |  ${START}Left("a").right.get$END
       |}
      """
  }
}
