package org.jetbrains.plugins.scala.lang.formatter.tests

import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

class ScalaFmtSelectionTest extends SelectionTest {
  override def setUp(): Unit = {
    super.setUp()
    getScalaSettings.FORMATTER = ScalaCodeStyleSettings.SCALAFMT_FORMATTER
  }

  def testExprSelection(): Unit = {
    val before =
      s"class Test { val v = ${startMarker}1    +     22  $endMarker}"
    val after = "class Test { val v = 1 + 22 }"
    doTextTest(before, after)
  }

  def testStatementSelection(): Unit = {
    val before =
      s"""
         |class Test {
         |  def foo(): Unit = {
         |    ${startMarker}println(42    +   22)$endMarker
         |  }
         |}
      """.stripMargin
    val after =
      """
        |class Test {
        |  def foo(): Unit = {
        |    println(42 + 22)
        |  }
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testPreserveBadFormatting(): Unit = {
    val before =
      s"""
         |class Test {
         |  def foo(): Unit = {
         |    ${startMarker}pri${endMarker}ntln(42   +   2)
         |  }
         |}
       """.stripMargin
    val after =
      """
        |class Test {
        |  def foo(): Unit = {
        |    println(42   +   2)
        |  }
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testProperRangeWidening(): Unit = {
    val before =
      s"""
         |class Test {
         |  def foo(): Unit = {
         |    println( 42 $startMarker +  43  +  28$endMarker )
         |  }
         |}
       """.stripMargin
    val after =
      """
        |class Test {
        |  def foo(): Unit = {
        |    println( 42 + 43 + 28 )
        |  }
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testMultiLineRange(): Unit = {
    val before =
      s"""
         |class Test {
         |  def bar(): Test =
         |    foo(  "a" )$startMarker
         | .foo( "a" )
         |      .foo(   " a "$endMarker)
         |}
      """.stripMargin
    val after =
      """
        |class Test {
        |  def bar(): Test =
        |    foo(  "a" )
        |      .foo("a")
        |      .foo(" a ")
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testNestedMultiLineRange(): Unit = {
    val before =
      s"""
         |class Test {
         |  def bar(): Test = {
         |    ${startMarker}foo {
         |      val   x  =   "a"
         |      x  +   "b"
         |    $endMarker}
         |  }
         |}
       """.stripMargin
    val after =
      """
        |class Test {
        |  def bar(): Test = {
        |    foo {
        |      val x = "a"
        |      x + "b"
        |    }
        |  }
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testFormatErroneousCode(): Unit = {
    val before =
      s"""
         |class {
         |  val foo = pri${startMarker}ntln( 42   +   43 )$endMarker
         |}
       """.stripMargin
    val after =
      """
        |class {
        |  val foo = println(42 + 43)
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testFormatTypeDef(): Unit = {
    val before =
      s" ${startMarker}type T    =   String$endMarker"
    val after = "type T = String"
    doTextTest(before, after)
  }

  def testFormatValDef(): Unit = {
    val before = s" ${startMarker}val x=42$endMarker"
    val after = "val x = 42"
    doTextTest(before, after)
  }

  def testFormatFunDef(): Unit = {
    val before = s" ${startMarker}def foo= 42$endMarker"
    val after  = "def foo = 42"
    doTextTest(before, after)
  }

  def testFormatImports(): Unit = {
    val before = s" ${startMarker}import foo.bar.{baz,    foo,   bar}$endMarker"
    val after = "import foo.bar.{baz, foo, bar}"
    doTextTest(before, after)
  }

  def testFormatMultipleImports(): Unit = {
    val before =
      s"""
         |${startMarker}import foo.bar. {baz, foo  }
         |import foo.baz.{bar,baz}$endMarker
         |println(2  +  3)
       """.stripMargin
    val after =
      """
        |import foo.bar.{baz, foo}
        |import foo.baz.{bar, baz}
        |println(2  +  3)
      """.stripMargin
    doTextTest(before, after)
  }

  def testFormatMultipleIndented(): Unit = {
    val before =
      s"""
         |class Foo {
         |  println(   1  +  2)
         |println( 3 $startMarker  +        4)
         |       println(5+6)$endMarker
         |  println(7+8)
         |}
       """.stripMargin
    val after =
      s"""
         |class Foo {
         |  println(   1  +  2)
         |println( 3 + 4)
         |println(5 + 6)
         |  println(7+8)
         |}
       """.stripMargin
    doTextTest(before, after)
  }

  def testFormatMultipleIndented_2(): Unit = {
    val before =
      s"""
         |class Foo {
         |  println(   1  +  2)
         |  ${startMarker}println( 3   +        4)
         |println(   4   +5)
         |  println(5+6)$endMarker
         |  println(7+8)
         |}
       """.stripMargin
    val after =
      s"""
         |class Foo {
         |  println(   1  +  2)
         |  println(3 + 4)
         |  println(4 + 5)
         |  println(5 + 6)
         |  println(7+8)
         |}
       """.stripMargin
    doTextTest(before, after)
  }

  def testFormatMultipleIndented_3(): Unit = {
    val before =
      s"""
         |class Foo {
         |  println(   1  +  2)
         |${startMarker}println( 3   +        4)
         |  println(   4   +5)
         |  println(5+6)$endMarker
         |  println(7+8)
         |}
       """.stripMargin
    val after =
      s"""
         |class Foo {
         |  println(   1  +  2)
         |println(3 + 4)
         |println(4 + 5)
         |println(5 + 6)
         |  println(7+8)
         |}
       """.stripMargin
    doTextTest(before, after)
  }

  def testSCL13939(): Unit = {
    val before =
      s"""
         |object Test {
         |  null match {
         |    case _ =>
         |$startMarker      val nameOpt = {
         |        "foo"
         |      }
         |      val sourceCode = new StringBuilder()$endMarker
         |  }
         |}
      """.stripMargin
    val after =
      """
        |object Test {
        |  null match {
        |    case _ =>
        |      val nameOpt = {
        |        "foo"
        |      }
        |      val sourceCode = new StringBuilder()
        |  }
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testNPEStringContent(): Unit = {
    val tripleQuote = "\"\"\""
    val before =
      s"""
         |class Foo {
         |  $tripleQuote $startMarker aaa
         |aaa$endMarker
         |  $tripleQuote
         |}
       """.stripMargin
    val after =
      s"""
         |class Foo {
         |  $tripleQuote  aaa
         |aaa
         |  $tripleQuote
         |}
       """.stripMargin
    doTextTest(before, after)
  }

  def testSCL14113(): Unit = {
    val before =
      s"""
         |object Foo {
         |  ${startMarker}def toString() = "Foo"}$endMarker
       """.stripMargin
    val after =
      s"""
         |object Foo {
         |  def toString() = "Foo"
         |}
       """.stripMargin
    doTextTest(before, after)
  }

  def testWhitespaceSelection(): Unit = {
    val before = s"object O {$startMarker   ${endMarker}}"
    val after = s"object O {}"
    doTextTest(before, after)
  }

  def testWhitespaceSelection_1(): Unit = {
    val before = s"def    foo$startMarker     ${endMarker}=     42"
    val after = s"def    foo =     42"
    doTextTest(before, after)
  }

  def testWhitespaceSelection_2(): Unit = {
    val before = s"1   +$startMarker   ${endMarker}3   +   4"
    val after = s"1   + 3   +   4"
    doTextTest(before, after)
  }

  def testWhitespaceSelection_3(): Unit = {
    val before =
      s"""
         |class T {
         |$startMarker        ${endMarker}def   foo  =  42
         |}
       """.stripMargin
    val after =
      s"""
         |class T {
         |  def   foo  =  42
         |}
       """.stripMargin
    doTextTest(before, after)
  }

  def testObjectPartialSelection(): Unit = {
    val before =
      s"""
         |package foo
         |${startMarker}object O {
         |   def foo  =  bar
         |}
         |$endMarker
         |
       """.stripMargin
    val after =
      s"""
         |package foo
         |object O {
         |  def foo = bar
         |}
       """.stripMargin
    doTextTest(before, after)
  }

  def testWsBetweenPackageAndImport(): Unit = {
    val before =
      s"""
         |package foo$startMarker
         |  $endMarker  import bar._
         |class C {}
       """.stripMargin
    val after =
      s"""
         |package foo
         |import bar._
         |class C {}
       """.stripMargin
    doTextTest(before, after)
  }

  def testWsBetweenImportAndClass(): Unit = {
    val before =
      s"""
         |package foo
         |import bar._$startMarker
         |     ${endMarker}class C{}
       """.stripMargin
    val after =
      s"""
         |package foo
         |import bar._
         |class C{}
       """.stripMargin
    doTextTest(before, after)
  }
}
