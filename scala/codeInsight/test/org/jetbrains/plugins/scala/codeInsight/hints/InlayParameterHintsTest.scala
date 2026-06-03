package org.jetbrains.plugins.scala.codeInsight.hints

import org.jetbrains.plugins.scala.codeInsight.{InlayHintsTestBase, ScalaCodeInsightSettings}
import org.jetbrains.plugins.scala.util.ConfigureJavaFile.configureJavaFile

class InlayParameterHintsTest extends InlayHintsTestBase {

  import Hint.{End => E, Start => S}

  def testNoDefaultPackageHint(): Unit = doTest(
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
       |  pf.applyOrElse(42, identity[Int])
       |
       |  Seq(1, 2, 3).collect(pf)""".stripMargin
  )

  def testParameterHint(): Unit = doTest(
    s"""  def foo(foo: Int, otherFoo: Int = 42)
       |         (bar: Int)
       |         (baz: Int = 0): Unit = {}
       |
       |  foo(${S}foo = ${E}42, ${S}otherFoo = ${E}42)(${S}bar = ${E}42)()
       |  foo(${S}foo = ${E}42)(bar = 42)()
       |  foo(${S}foo = ${E}42, ${S}otherFoo = ${E}42)(${S}bar = ${E}42)(${S}baz = ${E}42)""".stripMargin
  )

  def testConstructorParameterHint(): Unit = doTest(
    s"""  new Bar(${S}bar = ${E}42)
       |  new Bar()
       |
       |  class Bar(bar: Int = 42)
       |
       |  new Baz()
       |
       |  class Baz""".stripMargin
  )

  def testNoInfixExpressionHint(): Unit = doTest(
    s"""object This {
       |  def foo(bar: Int): Int = bar
       |
       |  foo(${S}bar = ${E}this foo 42)
       |}""".stripMargin
  )

  def testNoTrivialHint(): Unit = doTest(
    s"""  def foo(bar: String): Unit = {}
       |  def foo(length: Int): Unit = {}
       |  def bar(hashCode: Int): Unit = {}
       |  def bar(classOf: Class[_]): Unit = {}
       |  def bar(baz: () => Unit): Unit = {}
       |
       |  val bar = ""
       |
       |  def bazImpl(): Unit = {}
       |
       |  foo(${S}bar = ${E}null)
       |  foo(bar)
       |  foo(bar.length)
       |  bar(bar.hashCode())
       |  bar(classOf[String])
       |  baz(bazImpl())""".stripMargin
  )

  def testVarargHint(): Unit = doTest(
    s"""  def foo(foo: Int, bars: Int*): Unit = {}
       |
       |  foo(${S}foo = ${E}42)
       |  foo(${S}foo = ${E}42, bars = 42, 42 + 0)
       |  foo(foo = 42)
       |  foo(foo = 42, ${S}bars = ${E}42, 42 + 0)
       |  foo(${S}foo = ${E}42, ${S}bars = ${E}42, 42 + 0)
       |  foo(foo = 42, bars = 42, 42 + 0)""".stripMargin
  )

  def testVarargConstructorHint(): Unit = doTest(
    s"""  new Foo(${S}foo = ${E}42)
       |  new Foo(${S}foo = ${E}42, bars = 42, 42 + 0)
       |  new Foo(foo = 42)
       |  new Foo(foo = 42, ${S}bars = ${E}42, 42 + 0)
       |  new Foo(${S}foo = ${E}42, ${S}bars = ${E}42, 42 + 0)
       |  new Foo(foo = 42, bars = 42, 42 + 0)
       |
       |  class Foo(foo: Int, bars: Int*)""".stripMargin
  )

  def testNoSyntheticParameterHint(): Unit = doTest(
    s"""  def foo: Int => Int = identity
       |
       |  foo(42)
       |  foo.apply(42)""".stripMargin
  )

  def testSingleCharacterParameterHint(): Unit = doTest(
    s"""  def foo(f: Int): Unit = {}
       |
       |  foo(42)
     """.stripMargin
  )

  def testNoFunctionalParameterHint(): Unit = doTest(
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
       |    foo(collector)
       |    foo({ case 42 => 42 })
       |  }
       |
       |  bar(${S}bar = ${E}0) {
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
    doTest(s"  Bar.bar(${S}bar = ${E}42)")
  }

  def testJavaConstructorParameterHint(): Unit = {
    configureJavaFile(
      fileText =
        """public class Bar {
          |  public Bar(int bar) {}
          |}""".stripMargin,
      className = "Bar.java"
    )
    doTest(s"  new Bar(${S}bar = ${E}42)")
  }

  def testVarargJavaConstructorHint(): Unit = {
    configureJavaFile(
      fileText =
        """public class Bar {
          |  public Bar(int foo, int... bars) {}
          |}""".stripMargin,
      className = "Bar.java"
    )
    doTest(
      s"""  new Bar(${S}foo = ${E}42)
         |  new Bar(${S}foo = ${E}42, bars = 42, 42 + 0)
         |  new Bar(foo = 42)
         |  new Bar(foo = 42, ${S}bars = ${E}42, 42 + 0)
         |  new Bar(${S}foo = ${E}42, ${S}bars = ${E}42, 42 + 0)
         |  new Bar(foo = 42, bars = 42, 42 + 0)""".stripMargin
    )
  }

  def testApplyUpdateParameterHints(): Unit = doTest(
    s"""  private val array: Array[Double] = Array.emptyDoubleArray
       |
       |  def apply(index: Int): Double = array(index)
       |
       |  this(${S}index = ${E}0)
       |  this.apply(${S}index = ${E}0)
       |
       |  def update(index: Int, value: Double): Unit = {
       |    array(index) = value
       |  }
       |
       |  this(${S}index = ${E}0) = 0d
       |  this.update(${S}index = ${E}0, ${S}value = ${E}0d)
       |
       |  Seq(1, 2, 3)
       |  Seq.apply(1, 2, 3)""".stripMargin
  )

  def testNonLiteralArgumentParameterHint(): Unit = doTest(
    s"""  def foo(length: Int): Unit = {}
       |  foo("".length())
       |
       |  def bar(hashCode: Int): Unit = {}
       |  bar("".hashCode())""".stripMargin
  )

  def testNoParameterHintsByCamelCase(): Unit = doTest(
    s"""  type Type = String
       |
       |  val `type`: Type = "type"
       |  def  getType: Type = "type"
       |  def withType(`type`: Type): Unit = {}
       |
       |  withType(`type`)
       |  withType(getType)
       |  withType($S`type` = $E"type")""".stripMargin
  )

  private def doTest(text: String): Unit = {
    val settings = ScalaCodeInsightSettings.getInstance
    try {
      settings.showParameterNames = true
      doInlayTest(text)
    } finally {
      settings.showParameterNames = false
    }
  }
}
