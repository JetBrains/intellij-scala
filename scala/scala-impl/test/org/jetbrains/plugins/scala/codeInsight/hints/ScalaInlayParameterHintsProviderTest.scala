package org.jetbrains.plugins.scala
package codeInsight
package hints

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.Assert.{assertFalse, assertTrue}

class ScalaInlayParameterHintsProviderTest extends ScalaLightCodeInsightFixtureTestAdapter {

  import ScalaInlayParameterHintsProvider._
  import ScalaInlayParameterHintsProviderTest.{HintEnd => E, HintStart => S}

  def testNoDefaultPackageHint(): Unit = doParameterTest(
    s"""  println(42)
       |
       |  Some(42)
       |
       |  Option(null)
       |
       |  identity[Int].apply(42)
       |
       |  val pf: PartialFunction[Int, Int] = {
       |    case 42 => 42
       |  }
       |  pf.applyOrElse(42, identity[Int])""".stripMargin
  )

  def testParameterHint(): Unit = doParameterTest(
    s"""  def foo(foo: Int, otherFoo: Int = 42)
       |         (bar: Int)
       |         (baz: Int = 0): Unit = {}
       |
       |  foo(${S}foo =${E}42, ${S}otherFoo =${E}42)(${S}bar =${E}42)()
       |  foo(${S}foo =${E}42)(bar = 42)()
       |  foo(${S}foo =${E}42, ${S}otherFoo =${E}42)(${S}bar =${E}42)(${S}baz =${E}42)""".stripMargin
  )

  def testConstructorParameterHint(): Unit = doParameterTest(
    s"""  new Bar(${S}bar =${E}42)
       |  new Bar()
       |
       |  class Bar(bar: Int = 42)
       |
       |  new Baz()
       |
       |  class Baz""".stripMargin
  )

  def testNoInfixExpressionHint(): Unit = doParameterTest(
    s"""  def foo(foo: Int): Unit = {}
       |
       |  this foo 42""".stripMargin
  )

  def testNoTrivialHint(): Unit = doParameterTest(
    s"""  def foo(bar: String): Unit = {}
       |  def foo(length: Int): Unit = {}
       |
       |  val bar = ""
       |
       |  foo(bar)
       |  foo(bar.length)""".stripMargin
  )

  def testVarargHint(): Unit = doParameterTest(
    s"""  def foo(foo: Int, bars: Int*): Unit = {}
       |
       |  foo(${S}foo =${E}42)
       |  foo(${S}foo =${E}42, bars = 42, 42 + 0)
       |  foo(foo = 42)
       |  foo(foo = 42, ${S}bars =${E}42, 42 + 0)
       |  foo(${S}foo =${E}42, ${S}bars =${E}42, 42 + 0)
       |  foo(foo = 42, bars = 42, 42 + 0)""".stripMargin
  )

  def testVarargConstructorHint(): Unit = doParameterTest(
    s"""  new Foo(${S}foo =${E}42)
       |  new Foo(${S}foo =${E}42, bars = 42, 42 + 0)
       |  new Foo(foo = 42)
       |  new Foo(foo = 42, ${S}bars =${E}42, 42 + 0)
       |  new Foo(${S}foo =${E}42, ${S}bars =${E}42, 42 + 0)
       |  new Foo(foo = 42, bars = 42, 42 + 0)
       |
       |  class Foo(foo: Int, bars: Int*)""".stripMargin
  )

  def testNoSyntheticParameterHint(): Unit = doParameterTest(
    s"""  def foo: Int => Int = identity
       |
       |  foo(42)
       |  foo.apply(42)""".stripMargin
  )

  def testNoFunctionalParameterHint(): Unit = doParameterTest(
    s"""  def foo(pf: PartialFunction[Int, Int]): Unit = {
       |    pf(42)
       |    pf.apply(42)
       |  }
       |
       |  foo {
       |    case 42 => 42
       |  }
       |
       |  def bar(bar: Int = 42)
       |         (collector: PartialFunction[Int, Int]): Unit = {
       |    pf(bar)
       |    pf.apply(bar)
       |
       |    foo(${S}pf =${E}collector)
       |    foo({ case 42 => 42 })
       |  }
       |
       |  bar(${S}bar =${E}0) {
       |    case 42 => 42
       |  }""".stripMargin
  )

  def testJavaParameterHint(): Unit = {
    configureJavaFile(
      fileText =
        """public class Bar {
          |  public static void bar(int bar) {}
          |}""".stripMargin,
      className = "Bar.java"
    )
    doParameterTest(s"  Bar.bar(${S}bar =${E}42)")
  }

  def testJavaConstructorParameterHint(): Unit = {
    configureJavaFile(
      fileText =
        """public class Bar {
          |  public Bar(int bar) {}
          |}""".stripMargin,
      className = "Bar.java"
    )
    doParameterTest(s"  new Bar(${S}bar =${E}42)")
  }

  def testVarargJavaConstructorHint(): Unit = {
    configureJavaFile(
      fileText =
        """public class Bar {
          |  public Bar(int foo, int... bars) {}
          |}""".stripMargin,
      className = "Bar.java"
    )
    doParameterTest(
      s"""  new Bar(${S}foo =${E}42)
         |  new Bar(${S}foo =${E}42, bars = 42, 42 + 0)
         |  new Bar(foo = 42)
         |  new Bar(foo = 42, ${S}bars =${E}42, 42 + 0)
         |  new Bar(${S}foo =${E}42, ${S}bars =${E}42, 42 + 0)
         |  new Bar(foo = 42, bars = 42, 42 + 0)""".stripMargin
    )
  }

  def testFunctionReturnTypeHint(): Unit = doTest(
    s"""  def foo()$S: List[String]$E = List.empty[String]"""
  )(hintType = ReturnTypeHintType)

  def testNoFunctionReturnTypeHint(): Unit = doTest(
    """  def foo(): List[String] = List.empty[String]"""
  )(hintType = ReturnTypeHintType)

  def testPropertyTypeHint(): Unit = doTest(
    s"""  val list$S: List[String]$E = List.empty[String]"""
  )(hintType = PropertyHintType)

  def testNoPropertyTypeHint(): Unit = doTest(
    """  val list: List[String] = List.empty[String]"""
  )(hintType = PropertyHintType)

  def testLocalVariableTypeHint(): Unit = doTest(
    s"""  def foo(): Unit = {
       |    val list$S: List[String]$E = List.empty[String]
       |  }""".stripMargin
  )(hintType = LocalVariableHintType)

  def testNoLocalVariableTypeHint(): Unit = doTest(
    s"""  def foo(): Unit = {
       |    val list: List[String] = List.empty[String]
       |  }""".stripMargin
  )(hintType = LocalVariableHintType)

  private def doTest(text: String)
                    (hintType: OptionHintType): Unit = {
    import hintType._
    assertFalse(isOptionEnabled)
    enable()
    assertTrue(isOptionEnabled)

    //    assertTrue(isOptionEnabled) // TODO ???
    disable()
    assertFalse(isOptionEnabled)
  }

  private def doParameterTest(text: String): Unit = {
    configureFromFileText(
      s"""class Foo {
         |$text
         |}
         |
         |new Foo""".stripMargin
    )
    getFixture.testInlays()
  }
}

object ScalaInlayParameterHintsProviderTest {

  private val HintStart = "<hint text=\""
  private val HintEnd = "\" />"
}
