package org.jetbrains.plugins.scala
package codeInspection
package controlFlow

import com.intellij.codeInspection.LocalInspectionTool

abstract class UnusedExpressionInspectionTestBase extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ScalaUnusedExpressionInspection]
}

class UnusedExpressionInspectionTest extends UnusedExpressionInspectionTestBase {

  override protected val description =
    ScalaInspectionBundle.message("unused.expression.no.side.effects")

  def testLiteral(): Unit = checkTextHasError {
    s"""def foo(): Int = {
       |    if (true) return 1
       |    else ${START}2$END
       |
       |    0
       |}"""
  }

  def testTuple(): Unit = checkTextHasError {
    s"""def foo(): Int = {
       |    var x = 0
       |    $START(0, 2)$END
       |    0
       |}"""
  }

  def testReference(): Unit = checkTextHasError {
    s"""def foo(): Int = {
       |    $START(0, 2)._1$END
       |    0
       |}"""
  }

  def testReferenceToVal(): Unit = checkTextHasError {
    s"""def foo(): Int = {
       |  val a = 1
       |  ${START}a$END
       |  0
       |}"""
  }

  def testTypedAndParenthesized(): Unit = checkTextHasError {
    s"""def foo(): Int = {
       |  val s = "aaa"
       |  $START(s: String).substring(0)$END
       |  0
       |}"""
  }

  def testReferenceToByNameParam(): Unit = checkTextHasNoErrors {
    s"""def foo(i: => Int): Int = {
       |  i
       |  0
       |}"""
  }

  def testStringBuffer(): Unit = checkTextHasNoErrors {
    s"""def foo(): Int = {
       |  val b = new StringBuffer()
       |  b.append("a")
       |  0
       |}"""
  }

  def testObjectMethodWithSideEffects(): Unit = checkTextHasNoErrors {
    s"""def foo(): Int = {
       |  "1".wait()
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
       |    case 0 => List(1).foreach(f)
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

  def testUnusedFunctionRef(): Unit = checkTextHasError {
    s"""
       |def foo(): Unit = {
       | val f = foo _
       |  ${START}f$END
       |}
       |""".stripMargin
  }

  def testUsedFunctionRef(): Unit = checkTextHasNoErrors {
    s"""
       |def foo: Unit = {
       |  foo
       |}
       |""".stripMargin
  }

  def testUnusedPrimitiveParam(): Unit = checkTextHasError {
    s"""
      |def foo(i: Int): Unit = {
      |  ${START}i$END
      |}
      |""".stripMargin
  }

  def testUnusedStableReferenceParam(): Unit = checkTextHasError {
    s"""
       |def foo(s: Seq[Int]): Unit = {
       |  ${START}s$END
       |}
       |""".stripMargin
  }

  def testFunctionalParam(): Unit = checkTextHasNoErrors {
    s"""def foo(f: Int => Unit): Unit = {
       |  List(1) foreach f
       |}
       """
  }

  def testUnusedFunctionalParam(): Unit = checkTextHasError {
    s"""
       |def foo(f: () => Int): Unit = {
       |  ${START}f$END
       |}
       |""".stripMargin
  }

  def testUsedFunctionalParam(): Unit = checkTextHasNoErrors {
    s"""
       |def foo(f: () => Unit) = {
       |  f
       |}
       |""".stripMargin
  }

  def testUnusedFunctionalParamWhenUsedAfter(): Unit = checkTextHasError {
    s"""
       |def foo(f: () => Int) = {
       |  ${START}f$END
       |  f
       |}
       |""".stripMargin
  }

  def testLazyFunctionalParam(): Unit = checkTextHasNoErrors {
    s"""
       |def foo(f: => Unit): Unit = {
       |  f
       |}
       |""".stripMargin
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
       |    x += 1
       |    0
       |}"""
  }

  def testNoForAssignment2(): Unit = checkTextHasNoErrors {
    s"""def foo(): Int = {
       |    var x = 0
       |    x = 1
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
       |  "1".wait()
       |}"""
  }

  def testImplicitClass(): Unit = checkTextHasNoErrors {
    s"""implicit class StringOps(val s: String) {
       |  def print(): Unit = println(s)
       |}
       |
       |def foo(): Unit = {
       |  "1".print()
       |}"""
  }

  def testImplicitFunction(): Unit = checkTextHasNoErrors {
    s"""implicit def stringToInteger(s: String): Integer = Integer.valueOf(s.length)
       |
       |def foo(): Unit = {
       |  $START"1".intValue()$END
       |}"""
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

  def test_SCL15653_0(): Unit = checkTextHasError(
    s"""
       |object Main extends App {
       |  def executeSum(a: Int, b: Int): Unit = ${START}a + b$END
       |  def executeWrong(f: () => Unit): Unit = f // not used or converted to Unit
       |  def executeRight(f: () => Unit): Unit = f()
       |  executeWrong(() => println("there"))
       |}
       |""".stripMargin,
    allowAdditionalHighlights = true
  )

  def test_SCL15653_1(): Unit = checkTextHasError(
    s"""
      |object Main extends App {
      |  def executeSum(a: Int, b: Int): Unit = a + b
      |  def executeWrong(f: () => Unit): Unit = ${START}f$END // not used or converted to Unit
      |  def executeRight(f: () => Unit): Unit = f()
      |  executeWrong(() => println("there"))
      |}
      |""".stripMargin,
    allowAdditionalHighlights = true
  )

  def test_SCL15653_2(): Unit = checkTextHasError(
    s"""
       |object Main extends App {
       |  def executeSum(a: Int, b: Int): Unit = a + b
       |  def executeWrong(f: () => Unit): Unit = f // not used or converted to Unit
       |  def executeDoubleWrong(f: () => Unit): Unit = {
       |    ${START}f$END // not used
       |    f // not used or converted to Unit
       |  }
       |  def executeRight(f: () => Unit): Unit = f()
       |  executeWrong(() => println("there"))
       |}
       |""".stripMargin,
    allowAdditionalHighlights = true
  )

  def test_SCL15653_3(): Unit = checkTextHasError(
    s"""
       |object Main extends App {
       |  def executeSum(a: Int, b: Int): Unit = a + b
       |  def executeWrong(f: () => Unit): Unit = f // not used or converted to Unit
       |  def executeDoubleWrong(f: () => Unit): Unit = {
       |    f // not used
       |    ${START}f$END // not used or converted to Unit
       |  }
       |  def executeRight(f: () => Unit): Unit = f()
       |  executeWrong(() => println("there"))
       |}
       |""".stripMargin,
    allowAdditionalHighlights = true
  )

  // SCL-18641
  def test_assignment_in_methodcall(): Unit = checkTextHasNoErrors(
    """
      |{
      |  var res = "new Test"
      |  res.+=(3)
      |
      |  ()
      |}
      |""".stripMargin
  )

  def test_object(): Unit = checkTextHasError(
    s"""
       |object Blub
       |
       |def test(): Unit = {
       |  ${START}Blub$END
       |}
       |""".stripMargin
  )

  def test_val_reference(): Unit = checkTextHasError(
    s"""
       |object Test {
       |  val x = 0
       |  ${START}x$END
       |}
       |""".stripMargin
  )

  def test_while_block(): Unit = checkTextHasError(
    s"""
       |while (true) {
       |  ${START}3$END
       |}
       |""".stripMargin
  )

  def test_while_direct(): Unit = checkTextHasError(
    s"""
       |while (true)
       |  ${START}3$END
       |""".stripMargin
  )

  def test_do_while_direct(): Unit = checkTextHasError(
    s"""
       |do ${START}3$END while (true)
       |""".stripMargin
  )

  def test_for_block(): Unit = checkTextHasError(
    s"""
       |for (a <- Sed(1)) {
       |  ${START}3$END
       |}
       |""".stripMargin
  )

  def test_for_direct(): Unit = checkTextHasError(
    s"""
       |for (a <- Sed(1))
       |  ${START}3$END
       |""".stripMargin
  )

  def test_for_yield(): Unit = checkTextHasNoErrors(
    s"""
       |for (a <- Sed(1))
       |  yield 3
       |""".stripMargin
  )


  def test_finally(): Unit = checkTextHasError(
    s"""
       |def test(): Int = {
       |  try 3
       |  finally ${START}3$END
       |}
       |""".stripMargin
  )
}

class UnusedExpressionThrowsInspectionTest extends UnusedExpressionInspectionTestBase {

  override protected val description =
    ScalaInspectionBundle.message("unused.expression.throws")

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
