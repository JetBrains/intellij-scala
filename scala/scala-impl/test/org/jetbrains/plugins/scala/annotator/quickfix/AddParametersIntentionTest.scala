package org.jetbrains.plugins.scala.annotator.quickfix

import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase

class AddParametersIntentionTest extends ScalaAnnotatorQuickFixTestBase {

  override protected def description: String = ???

  override protected def descriptionMatches(s: String): Boolean = s.startsWith("Too many arguments")

  def test1ArgToMuch(): Unit = testQuickFix(
    s"""
       |object Foo {
       |  def doSomething(flag: Boolean) {}
       |
       |  doSomething(true,$CARET false)
       |}
    """.stripMargin,
    s"""
       |object Foo {
       |  def doSomething(flag: Boolean, arg1: Boolean)$CARET {}
       |
       |  doSomething(true, false)
       |}
    """.stripMargin,
    "Add parameter(s) to method"
  )

  def test2ArgsToMunch(): Unit = testQuickFix(
    s"""
       |object Foo {
       |  def doSomething(flag: Boolean) {}
       |
       |  doSomething(true,$CARET 2, false)
       |}
    """.stripMargin,
    s"""
       |object Foo {
       |  def doSomething(flag: Boolean, arg1: Int, arg2: Boolean)$CARET {}
       |
       |  doSomething(true, 2, false)
       |}
    """.stripMargin,
    "Add parameter(s) to method"
  )

  def testListArg(): Unit = testQuickFix(
    s"""
       |object Foo {
       |  val list: List[String] = ???
       |
       |  def doSomething(flag: Boolean) {}
       |
       |  doSomething(true, ${CARET}list)
       |}
    """.stripMargin,
    s"""
       |object Foo {
       |  val list: List[String] = ???
       |
       |  def doSomething(flag: Boolean, arg1: List[String])$CARET {}
       |
       |  doSomething(true, list)
       |}
    """.stripMargin,
    "Add parameter(s) to method"
  )

  def testCaseClassArg(): Unit = testQuickFix(
    s"""
       |case class Foo(n: Int)
       |
       |object Foo {
       |  def doSomething(flag: Boolean) {}
       |
       |  doSomething(true,$CARET Foo(1))
       |}
    """.stripMargin,
    s"""
       |case class Foo(n: Int)
       |
       |object Foo {
       |  def doSomething(flag: Boolean, arg1: Foo)$CARET {}
       |
       |  doSomething(true, Foo(1))
       |}
    """.stripMargin,
    "Add parameter(s) to method"
  )

  def testNoArgYet(): Unit = testQuickFix(
    s"""
       |object Foo {
       |  def doSomething() {}
       |
       |  doSomething(${CARET}true)
       |}
    """.stripMargin,
    s"""
       |object Foo {
       |  def doSomething(arg1: Boolean)$CARET {}
       |
       |  doSomething(true)
       |}
    """.stripMargin,
    "Add parameter(s) to method"
  )

  def testFunType(): Unit = testQuickFix(
    s"""
       |case class Foo(n: Int)
       |
       |object Foo {
       |  def doSomething(flag: Boolean, n: Int) {}
       |
       |  val r = (x: Int) => Foo(x)
       |  doSomething(true, r,$CARET Foo(1), 1)
       |}
    """.stripMargin,
    s"""
       |case class Foo(n: Int)
       |
       |object Foo {
       |  def doSomething(flag: Boolean, arg1: Int => Foo, arg2: Foo, n: Int)$CARET {}
       |
       |  val r = (x: Int) => Foo(x)
       |  doSomething(true, r, Foo(1), 1)
       |}
    """.stripMargin,
    "Add parameter(s) to method"
  )

  def testMultipleParamLists(): Unit = testQuickFix(
    s"""
       |object Foo {
       |  def test(a: Int)(b: Int) = 0
       |
       |  test(3)(3,$CARET 3)
       |}
       |""".stripMargin,
    s"""
       |object Foo {
       |  def test(a: Int)(b: Int, arg1: Int)$CARET = 0
       |
       |  test(3)(3, 3)
       |}
       |""".stripMargin,
    "Add parameter(s) to method"
  )

  def testMultipleParamListsWithApply(): Unit = testQuickFix(
    s"""
       |class Foo {
       |  def apply(a: Int)(b: Int) = 0
       |
       |  def test(a: Int)(b: Int): Foo = 0
       |
       |  (test(1)(2)(3))(4,$CARET 4)
       |}
       |""".stripMargin,
    s"""
       |class Foo {
       |  def apply(a: Int)(b: Int, arg1: Int)$CARET = 0
       |
       |  def test(a: Int)(b: Int): Foo = 0
       |
       |  (test(1)(2)(3))(4, 4)
       |}
       |""".stripMargin,
    "Add parameter(s) to method"
  )

  def testReturnedLambda(): Unit = checkIsNotAvailable(
    s"""
       |class Foo {
       |  def test(a: Int): Int => Int = 0
       |
       |  test(1)(2,$CARET 3)
       |}
       |""".stripMargin,
    "Add parameter(s) to method"
  )
}
