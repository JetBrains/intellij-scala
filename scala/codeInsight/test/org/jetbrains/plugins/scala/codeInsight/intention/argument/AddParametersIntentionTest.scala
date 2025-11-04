package org.jetbrains.plugins.scala.codeInsight.intention.argument

import org.jetbrains.plugins.scala.codeInsight.{ScalaCodeInsightBundle, intentions}

class AddParametersIntentionTest extends intentions.ScalaIntentionTestBase {

  override def familyName: String = ScalaCodeInsightBundle.message("family.name.add.parameter")

  def test1(): Unit = {
    val text =
      s"""
         |object Foo {
         |  def doSomething(flag: Boolean) {}
         |
         |  doSomething(true, false${CARET})
         |}
      """.stripMargin
    val resultText =
      s"""
         |object Foo {
         |  def doSomething(flag: Boolean, arg1: Boolean)${CARET} {}
         |
         |  doSomething(true, false)
         |}
      """.stripMargin

    doTest(text, resultText)
  }

  def test2(): Unit = {
    val text =
      s"""
         |object Foo {
         |  def doSomething(flag: Boolean) {}
         |
         |  doSomething(true, 2, false${CARET})
         |}
      """.stripMargin
    val resultText =
      s"""
         |object Foo {
         |  def doSomething(flag: Boolean, arg1: Int, arg2: Boolean)${CARET} {}
         |
         |  doSomething(true, 2, false)
         |}
      """.stripMargin

    doTest(text, resultText)
  }

  def test3(): Unit = {
    val text =
      s"""
         |object Foo {
         |  def doSomething(flag: Boolean) {}
         |
         |  doSomething(1${CARET}, true)
         |}
      """.stripMargin
    val resultText =
      s"""
         |object Foo {
         |  def doSomething(arg1: Int, flag: Boolean)${CARET} {}
         |
         |  doSomething(1, true)
         |}
      """.stripMargin

    doTest(text, resultText)
  }

  def test4(): Unit = {
    val text =
      s"""
         |object Foo {
         |  val list: List[String] = ???
         |
         |  def doSomething(flag: Boolean) {}
         |
         |  doSomething(true, list${CARET})
         |}
      """.stripMargin
    val resultText =
      s"""
         |object Foo {
         |  val list: List[String] = ???
         |
         |  def doSomething(flag: Boolean, arg1: List[String])${CARET} {}
         |
         |  doSomething(true, list)
         |}
      """.stripMargin

    doTest(text, resultText)
  }

  def test5(): Unit = {
    val text =
      s"""
         |case class Foo(n: Int)
         |
         |object Foo {
         |  def doSomething(flag: Boolean) {}
         |
         |  doSomething(true, Foo(1)${CARET})
         |}
      """.stripMargin
    val resultText =
      s"""
         |case class Foo(n: Int)
         |
         |object Foo {
         |  def doSomething(flag: Boolean, arg1: Foo)${CARET} {}
         |
         |  doSomething(true, Foo(1))
         |}
      """.stripMargin

    doTest(text, resultText)
  }

  def test6(): Unit = {
    val text =
      s"""
         |object Foo {
         |  def doSomething() {}
         |
         |  doSomething(true${CARET})
         |}
      """.stripMargin
    val resultText =
      s"""
         |object Foo {
         |  def doSomething(arg1: Boolean)${CARET} {}
         |
         |  doSomething(true)
         |}
      """.stripMargin

    doTest(text, resultText)
  }

  def test7(): Unit = {
    val text =
      s"""
         |case class Foo(n: Int)
         |
         |object Foo {
         |  def doSomething(flag: Boolean, n: Int) {}
         |
         |  val r = (x: Int) => Foo(x)
         |  doSomething(true, r, Foo(1)${CARET}, 1)
         |}
      """.stripMargin
    val resultText =
      s"""
         |case class Foo(n: Int)
         |
         |object Foo {
         |  def doSomething(flag: Boolean, arg1: Int => Foo, arg2: Foo, n: Int)${CARET} {}
         |
         |  val r = (x: Int) => Foo(x)
         |  doSomething(true, r, Foo(1), 1)
         |}
      """.stripMargin

    doTest(text, resultText)
  }

  def testMultipleParamLists(): Unit = doTest(
    s"""
       |object Foo {
       |  def test(a: Int)(b: Int) = 0
       |
       |  test(3)(3, 3$CARET)
       |}
       |""".stripMargin,
    s"""
       |object Foo {
       |  def test(a: Int)(b: Int, arg1: Int)$CARET = 0
       |
       |  test(3)(3, 3)
       |}
       |""".stripMargin
  )

  def testMultipleParamListsWithApply(): Unit = doTest(
    s"""
       |class Foo {
       |  def apply(a: Int)(b: Int) = 0
       |
       |  def test(a: Int)(b: Int): Foo = 0
       |
       |  (test(1)(2)(3))(4, 4$CARET)
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
       |""".stripMargin
  )

  def testReturnedLambda(): Unit = checkIntentionIsNotAvailable(
    s"""
       |class Foo {
       |  def test(a: Int): Int => Int = 0
       |
       |  test(1)(2, 3$CARET)
       |}
       |""".stripMargin
  )
}
