package org.jetbrains.plugins.scala.lang.formatter.scalafmt

import org.jetbrains.plugins.scala.util.Markers
import org.junit.{Ignore, Test}
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

class ScalaFmtCommonSelectionTest extends ScalaFmtCommonSelectionTestBase

class ScalaFmtCommonSelectionTest_2_7 extends ScalaFmtCommonSelectionTestBase with UseConfig_2_7

//noinspection RedundantBlock
@RunWith(classOf[JUnit4])
abstract class ScalaFmtCommonSelectionTestBase extends ScalaFmtSelectionTestBase with Markers {

  @Test
  def exprSelection(): Unit =
    doTextTest(
      s"class Test { val v = ${startMarker}1    +     22  $endMarker}",
      s"class Test { val v = ${startMarker}1 + 22 $endMarker}"
    )

  @Test
  def exprSelection_1(): Unit =
    doTextTest(
      s"class Test { val v = ${startMarker}1    +     22 $endMarker }",
      s"class Test { val v = ${startMarker}1 + 22 $endMarker }"
    )

  @Test
  def exprSelection_2(): Unit =
    doTextTest(
      s"class Test { val v =    ${startMarker}1    +     22$endMarker     }",
      s"class Test { val v = ${startMarker}1 + 22$endMarker     }"
    )

  @Test
  def exprSelection_3(): Unit =
    doTextTest(
      s"class Test { val v =    ${startMarker}1    +     22   $endMarker  }",
      s"class Test { val v = ${startMarker}1 + 22   $endMarker  }"
    )

  @Test
  def exprSelection_4(): Unit =
    doTextTest(
      s"class Test { val v =    ${startMarker}1    +     22     $endMarker}",
      s"class Test { val v = ${startMarker}1 + 22 $endMarker}"
    )

  @Test
  def statementSelection(): Unit = {
    val before =
      s"""
         |class Test {
         |  def foo(): Unit = {
         |    ${startMarker}println(42    +   22)$endMarker
         |  }
         |}
         |""".stripMargin
    val after =
      """
        |class Test {
        |  def foo(): Unit = {
        |    println(42 + 22)
        |  }
        |}
        |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def statementSelectionInsideSingleLineMethodBody(): Unit = {
    val before =
      s"""class Test2 {
         |  def foo(): Unit =
         |    ${startMarker}println(42  +  22)$endMarker
         |}""".stripMargin
    val after =
      s"""class Test2 {
         |  def foo(): Unit =
         |    println(42 + 22)
         |}""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def preserveBadFormatting(): Unit = {
    val before =
      s"""
         |class Test {
         |  def foo(): Unit = {
         |    ${startMarker}pri${endMarker}ntln(42   +   2)
         |  }
         |}
         |""".stripMargin
    val after =
      """
        |class Test {
        |  def foo(): Unit = {
        |    println(42   +   2)
        |  }
        |}
        |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def properRangeWidening(): Unit = {
    val before =
      s"""
         |class Test {
         |  def foo(): Unit = {
         |    println( 42 $startMarker +  43  +  28$endMarker )
         |  }
         |}
         |""".stripMargin
    val after =
      """
        |class Test {
        |  def foo(): Unit = {
        |    println( 42 + 43 + 28 )
        |  }
        |}
        |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def multiLineRange(): Unit = {
    val before =
      s"""
         |class Test {
         |  def bar(): Test =
         |    foo(  "a" )$startMarker
         | .foo( "a" )
         |      .foo(   " a "$endMarker)
         |}
         |""".stripMargin
    val after =
      """
        |class Test {
        |  def bar(): Test =
        |    foo(  "a" )
        |      .foo("a")
        |      .foo(" a ")
        |}
        |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def nestedMultiLineRange(): Unit = {
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
         |""".stripMargin
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
        |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def formatErroneousCode(): Unit = {
    val before =
      s"""
         |class {
         |  val foo = pri${startMarker}ntln( 42   +   43 )$endMarker
         |}
         |""".stripMargin
    val after =
      """
        |class {
        |  val foo = println(42 + 43)
        |}
        |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def formatTypeDef(): Unit = {
    val before =
      s" ${startMarker}type T    =   String$endMarker"
    val after = "type T = String"
    doTextTest(before, after)
  }

  @Test
  def formatTypeDefInsideObject_1(): Unit = {
    val before =
      s"""object TestClass {
         |      ${startMarker}type T = String
         |type X = Int$endMarker
         |}""".stripMargin
    val after =
      s"""object TestClass {
         |  type T = String
         |  type X = Int
         |}""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def formatTypeDefInsideObject_2(): Unit = {
    val before =
      s"""object TestClass {
         |      ${startMarker}type    T =     String
         |type    X =    Int$endMarker
         |}""".stripMargin
    val after =
      s"""object TestClass {
         |  type T = String
         |  type X = Int
         |}""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def formatValDef(): Unit = {
    val before = s" ${startMarker}val x=42$endMarker"
    val after = "val x = 42"
    doTextTest(before, after)
  }

  @Test
  def formatFunDef(): Unit = {
    val before = s" ${startMarker}def foo= 42$endMarker"
    val after = "def foo = 42"
    doTextTest(before, after)
  }

  @Test
  def formatImports(): Unit = {
    val before = s"    ${startMarker}import foo.bar.{baz,    foo,   bar}$endMarker"
    val after = "import foo.bar.{baz, foo, bar}\n"
    doTextTest(before, after)
  }

  @Test
  def formatMultipleImports(): Unit = {
    val before =
      s"""
         |${startMarker}import foo.bar. {baz, foo  }
         |import foo.baz.{bar,baz}$endMarker
         |println(2  +  3)
         |""".stripMargin
    val after =
      """
        |import foo.bar.{baz, foo}
        |import foo.baz.{bar, baz}
        |println(2  +  3)
        |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def formatMultipleImportsStartingOnFirstFileLine(): Unit = {
    val before =
      s"""$startMarker      ${startMarker(0)}import foo.bar. {baz, foo  }
         |  import foo.baz.{bar,baz}$endMarker${endMarker(0)}
         |println(2  +  3)
         |""".stripMargin
    val after =
      """import foo.bar.{baz, foo}
        |import foo.baz.{bar, baz}
        |println(2  +  3)
        |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def formatMultipleIndented(): Unit = {
    val before =
      s"""
         |class Foo {
         |  println(   1  +  2)
         |println( 3 $startMarker  +        4)
         |       println(5+6)$endMarker
         |  println(7+8)
         |}
         |""".stripMargin
    val after =
      s"""
         |class Foo {
         |  println(   1  +  2)
         |println( 3 + 4)
         |  println(5 + 6)
         |  println(7+8)
         |}
         |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def formatMultipleIndented_2(): Unit = {
    val before =
      s"""
         |class Foo {
         |  println(   1  +  2)
         |  ${startMarker}println( 3   +        4)
         |println(   4   +5)
         |  println(5+6)$endMarker
         |  println(7+8)
         |}
         |""".stripMargin
    val after =
      s"""
         |class Foo {
         |  println(   1  +  2)
         |  println(3 + 4)
         |  println(4 + 5)
         |  println(5 + 6)
         |  println(7+8)
         |}
         |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def formatMultipleIndented_3(): Unit = {
    val before =
      s"""
         |class Foo {
         |  println(   1  +  2)
         |${startMarker}println( 3   +        4)
         |  println(   4   +5)
         |println(5+6)$endMarker
         |  println(7+8)
         |}
         |""".stripMargin
    val after =
      s"""
         |class Foo {
         |  println(   1  +  2)
         |  println(3 + 4)
         |  println(4 + 5)
         |  println(5 + 6)
         |  println(7+8)
         |}
         |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def formatMultipleIndented_4(): Unit = {
    val before =
      s"""
         |class Foo {
         |  println(   1  +  2)
         |   ${startMarker}println( 3   +        4)
         |  println(   4   +5)
         |println(5+6)$endMarker
         |  println(7+8)
         |}
         |""".stripMargin
    val after =
      s"""
         |class Foo {
         |  println(   1  +  2)
         |  println(3 + 4)
         |  println(4 + 5)
         |  println(5 + 6)
         |  println(7+8)
         |}
         |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  @Ignore("SCL-23253")
  def SCL13939(): Unit = {
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
         |""".stripMargin
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
        |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def NPEStringContent(): Unit = {
    val qqq = "\"\"\""
    val before =
      s"""
         |class Foo {
         |  $qqq $startMarker aaa
         |aaa$endMarker
         |  $qqq
         |}
         |""".stripMargin
    val after =
      s"""
         |class Foo {
         |  $qqq  aaa
         |aaa
         |  $qqq
         |}
         |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def SCL14113(): Unit = {
    val before =
      s"""
         |object Foo {
         |  ${startMarker}def toString() = "Foo"$endMarker
         |}
         |""".stripMargin
    val after =
      s"""
         |object Foo {
         |  ${startMarker}def toString() = "Foo"$endMarker
         |}
         |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def whitespaceSelection(): Unit = {
    val before = s"object O {$startMarker   $endMarker}"
    val after = s"object O {}"
    doTextTest(before, after)
  }

  @Test
  def whitespaceSelection_1(): Unit = {
    val before = s"def    foo$startMarker     $endMarker=     42"
    val after = s"def    foo =     42"
    doTextTest(before, after)
  }

  @Test
  def whitespaceSelection_2(): Unit = {
    val before = s"1   +$startMarker   ${endMarker}3   +   4"
    val after = s"1   + 3   +   4"
    doTextTest(before, after)
  }

  @Test
  def whitespaceSelection_3(): Unit = {
    val before =
      s"""
         |class T {
         |$startMarker        ${endMarker}def   foo  =  42
         |}
         |""".stripMargin
    val after =
      s"""
         |class T {
         |  def   foo  =  42
         |}
         |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  @Ignore("SCL-23253")
  def objectPartialSelection(): Unit = {
    val before =
      s"""
         |package foo
         |${startMarker}object O {
         |   def foo  =  bar
         |}
         |$endMarker
         |
         |""".stripMargin
    val after =
      s"""
         |package foo
         |object O {
         |  def foo = bar
         |}
         |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def wsBetweenPackageAndImport(): Unit = {
    val before =
      s"""
         |package foo$startMarker
         |  $endMarker  import bar._
         |class C {}
         |""".stripMargin
    val after =
      s"""
         |package foo
         |import bar._
         |class C {}
         |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def wsBetweenImportAndClass(): Unit = {
    val before =
      s"""
         |package foo
         |import bar._$startMarker
         |     ${endMarker}class C{}
         |""".stripMargin
    val after =
      s"""
         |package foo
         |import bar._
         |class C{}
         |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def SCL14147(): Unit = {
    val before =
      s"""
         |class A[T]$startMarker
         |${endMarker}class B
         |""".stripMargin
    val after =
      s"""
         |class A[T]
         |class B
         |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  @Ignore("SCL-23253")
  def SCL14031(): Unit = {
    val before =
      s"""
         |object Outer {
         |trait T {
         |  val foo: Int
         |  val bar: Int
         |}
         |  ${startMarker}class T1 extends T {
         |override val foo: Int = ???
         |override val bar: Int = ???
         |}$endMarker
         |}
         |""".stripMargin
    val after =
      s"""
         |object Outer {
         |trait T {
         |  val foo: Int
         |  val bar: Int
         |}
         |  class T1 extends T {
         |    override val foo: Int = ???
         |    override val bar: Int = ???
         |  }
         |}
         |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def SCL14856_patternMatchInsideMethodDefinition_1(): Unit = {
    val before =
      s"""class TestClass {
         |  def parentScope: Unit = {
         |    ${startMarker}Option(1) match {
         |      case      Some(value) =>
         |      case      None =>
         |    }$endMarker
         |  }
         |}""".stripMargin
    val after =
      s"""class TestClass {
         |  def parentScope: Unit = {
         |    Option(1) match {
         |      case Some(value) =>
         |      case None        =>
         |    }
         |  }
         |}""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def SCL14856_patternMatchInsideMethodDefinition_2(): Unit = {
    val before =
      s"""class TestClass {
         |  def parentScope: Unit = {
         |    Option(1) match $startMarker{
         |      case   Some(value) =>
         |      case   None =>
         |    }$endMarker
         |  }
         |}""".stripMargin
    val after =
      s"""class TestClass {
         |  def parentScope: Unit = {
         |    Option(1) match {
         |      case Some(value) =>
         |      case None        =>
         |    }
         |  }
         |}""".stripMargin
    doTextTest(before, after)
  }

  @Test
  @Ignore("SCL-23253")
  def SCL14856_patternMatchInsideMethodDefinition_3(): Unit =
    doAllRangesTextTest(
      """class TestClass {
        |  def parentScope: Unit = {
        |    Option(1) match {
        |      case Some(value) =>
        |      case None        =>
        |    }
        |  }
        |}
        |""".stripMargin
    )

  @Test
  def SCL14856_patternMatchInsideClassFollowedByErroneousCode(): Unit = {
    val before =
      s"""class TestClass {
         |  def parentScope: Unit = {
         |${startMarker}Option(1) match {
         |case   Some(value) =>
         |case   None =>
         |}$endMarker
         |  }
         |}
         |
         |val x = 2 - "string"
         |trait X {}!#@""".stripMargin
    val after =
      s"""class TestClass {
         |  def parentScope: Unit = {
         |    Option(1) match {
         |      case Some(value) =>
         |      case None        =>
         |    }
         |  }
         |}
         |
         |val x = 2 - "string"
         |trait X {}!#@""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def formatObjectOrClassOrTraitName(): Unit = {
    val before =
      s"""object ${startMarker}O$endMarker {}
         |class ${startMarker(0)}C${endMarker(0)} {}
         |trait ${startMarker(1)}T${endMarker(1)} {}
         |""".stripMargin
    val after =
      s"""object O {}
         |class C {}
         |trait T {}
         |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def formatValInsideObjectSurroundedWithEmptyLines(): Unit = {
    val before =
      s"""object Outer {
         |
         |${startMarker}val x = 2 + 2$endMarker
         |
         |}""".stripMargin
    val after =
      s"""object Outer {
         |
         |  val x = 2 + 2
         |
         |}""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def formatValInRoot(): Unit = {
    val before =
      s"""  ${startMarker}val x=2   +   2
         |val y  =  4+4$endMarker
         |""".stripMargin
    val after =
      s"""val x = 2 + 2
         |val y = 4 + 4
         |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def formatValInRootSurroundedWithEmptyLines(): Unit = {
    val before =
      s"""
         |${startMarker}val x = 2 + 2
         |val y = 4 + 4$endMarker
         |
         |""".stripMargin
    val after =
      s"""
         |val x = 2 + 2
         |val y = 4 + 4
         |
         |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def differentTypesOfDefinitionsAtTopLevel(): Unit = {
    val before =
      s"""${startMarker}trait  X   {
         |  def xFoo = 42
         | }
         |object  O  {  }
         |class  C  {  }
         |val x=println(2+2)
         |def  foo (): Unit =  {
         |       42
         |       }$endMarker
         |""".stripMargin
    val after =
      s"""trait X {
         |  def xFoo = 42
         |}
         |object O {}
         |class C {}
         |val x = println(2 + 2)
         |def foo(): Unit = {
         |  42
         |}
         |""".stripMargin
    doTextTest(before, after)
  }

  @Test
  def scConstructorPattern_SCL15406(): Unit =
    doTextTest(
      s"""object Test {
         |  sealed trait SuperName
         |  case class Name(firstName: String, lastName: String) extends SuperName
         |
         |  def behavior(sn: SuperName): String = {
         |    sn match {
         |      case ${startMarker}Name${endMarker}(firstName, lastName) =>
         |        s"$$firstName$$lastName"
         |    }
         |  }
         |}
         |""".stripMargin
    )

  @Test
  def scConstructorPattern_SCL15406_AllRanges(): Unit =
    doAllRangesTextTest(
      s"""object Test {
         |  sealed trait SuperName
         |  case class Name(firstName: String, lastName: String) extends SuperName
         |
         |  ${startMarker}def behavior(sn: SuperName): String = {
         |    sn match {
         |      case Name(firstName, lastName) =>
         |        s"$$firstName$$lastName"
         |    }
         |  }${endMarker}
         |}
         |""".stripMargin
    )

  @Test
  def scConstructorPattern_SCL15406_1(): Unit =
    doTextTest(
      s"""object Test extends App {
         |  val some = Some(0)
         |  some match {
         |    case Some(test) => ${startMarker}test${endMarker} + 1
         |  }
         |}
         |""".stripMargin
    )

  @Test
  def scConstructorPattern_SCL15406_1_AllRanges(): Unit =
    doAllRangesTextTest(
      s"""object Test extends App {
         |  val some = Some(0)
         |  some match {
         |    case Some(test) => test + 1 + 2
         |  }
         |}
         |""".stripMargin
    )

  @Test
  def className_SCL15338(): Unit = {
    doTextTest(
      s"""object Test {
         |  class SomeClass{}
         |
         |  def foo(param: ${startMarker}SomeClass${endMarker}): SomeClass = {
         |    println(42)
         |    ???
         |  }
         |}
         |""".stripMargin
    )

    doTextTest(
      s"""object Test {
         |  trait SomeClass{}
         |
         |  def foo(param: SomeClass): ${startMarker}SomeClass${endMarker} = {
         |    println(42)
         |    ???
         |  }
         |}
         |""".stripMargin
    )
  }

  @Test
  def parametrizedClassName_SCL15338(): Unit = {
    doTextTest(
      s"""object Test {
         |  class SomeClass[T]{}
         |
         |  def foo(param: ${startMarker}SomeClass${endMarker}[String]): SomeClass[String] = {
         |    println(42)
         |    ???
         |  }
         |}
         |""".stripMargin
    )

    doTextTest(
      s"""object Test {
         |  trait SomeClass[T]{}
         |
         |  def foo(param: SomeClass[String]): ${startMarker}SomeClass${endMarker}[String] = {
         |    println(42)
         |    ???
         |  }
         |}
         |""".stripMargin
    )
  }

  @Test
  def removeWhitespaces(): Unit = doTextTest(
    s"""val x$startMarker : Int = 123$endMarker""",
    s"""val x: Int = 123"""
  )

  @Test
  def removeWhitespaces_1(): Unit = doTextTest(
    s"""val$startMarker   x   :   Int   =   123   +   42  $endMarker""",
    s"""val x: Int = 123 + 42  """
  )

  @Test
  def removeWhitespaces_2(): Unit = doTextTest(
    s"""val   x   :$startMarker   Int   =   123   +   42  $endMarker""",
    s"""val   x   : Int = 123 + 42  """
  )

  @Test
  def SCL14030(): Unit = {
    doTextTest(
      s"""object Example {
         |  trait Foo {
         |    def foo: Int
         |    def bar: Int
         |    def baz: Int
         |  }
         |
         |  val a: Foo = ${start}new Foo {override def bar: Int = ???
         |override def baz: Int = ???
         |override def qux: Int = ???
         |}${end}
         |}
         |""".stripMargin,
      s"""object Example {
         |  trait Foo {
         |    def foo: Int
         |    def bar: Int
         |    def baz: Int
         |  }
         |
         |  val a: Foo = new Foo {
         |    override def bar: Int = ???
         |    override def baz: Int = ???
         |    override def qux: Int = ???
         |  }
         |}
         |""".stripMargin
    )
  }
}
