package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class EnumResolveTest extends SimpleResolveTestBase {
  import SimpleResolveTestBase._

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testEnumClass(): Unit =
    doResolveTest(
      s"""
         |object Test {
         |  enum Foo { case Bar }
         |
         |  val f: F${REFSRC}oo = ???
         |}
         |""".stripMargin
    )


  def testSingletonCases(): Unit = {
    doResolveTest(
      s"""
         |object Test {
         |  enum Foo { case Bar; case Baz }
         |
         |  val f: Foo = Foo.B${REFSRC}ar
         |}
         |""".stripMargin
    )

    doResolveTest(
      s"""
         |object Test {
         |  enum Foo { case Bar; case Baz }
         |
         |  val f: Foo = Foo.B${REFSRC}az
         |}
         |""".stripMargin
    )
  }

  def testSingletonCasesImported(): Unit =
    doResolveTest(
      s"""
         |object Test {
         |  enum Foo { case Bar; case Baz }
         |  import Foo._
         |
         |  val f: Foo = B${REFSRC}az
         |}
         |""".stripMargin
    )


  def testSingletonMultipleDeclaredCases(): Unit =
    doResolveTest(
      s"""
         |object Test {
         |  enum Foo { case Bar, Baz, Qux }
         |  val v = Foo.Qu${REFSRC}x
         |}
         |""".stripMargin
    )

  def testValuesMethod(): Unit = {
    doResolveTest(
      s"""
         |object Test {
         |  enum Foo { case Bar, Baz, Qux }
         |  val v = Foo.v${REFSRC}alueOf("Bar")
         |}
         |""".stripMargin
    )

    doResolveTest(
      s"""
         |object Test {
         |  enum Foo { case Bar, Baz, Qux };
         |  val v = Foo.v${REFSRC}alues
         |}
         |""".stripMargin
    )
  }

  def testClassCase(): Unit =
    doResolveTest(
      s"""
         |object Test {
         |  enum Bar { case Foo(x: Int, y: String) }
         |  val b = Bar.Fo${REFSRC}o(1, "2")
         |}
         |""".stripMargin
    )

  def testMethodsOfEnum(): Unit =
    doResolveTest(
      s"""
         |object Test {
         |  enum Foo {
         |    def foo(): Int = 123
         |
         |    case Bar(x: Int)
         |  }
         |
         |  val foo = Foo.Bar.apply(1).f${REFSRC}oo()
         |}
         |""".stripMargin
    )

  def testValsOfEnum(): Unit =
    doResolveTest(
      s"""
         |object Test {
         |  enum Foo {
         |    val yy: Int = 123
         |
         |    case Bar(x: Int)
         |  }
         |
         |  val foo = Foo.Bar.apply(1).y${REFSRC}y
         |}
         |""".stripMargin
    )

  def testMethodsFromSupers(): Unit =
    doResolveTest(
      s"""
         |object Test {
         |  trait F { def foo(s: String): Int = ??? }
         |  enum Foo {
         |    case Bar extends Foo with F
         |  }
         |
         |  val f = Foo.Bar.f${REFSRC}oo("123")
         |}
         |""".stripMargin
    )

  def testRestrictedCaseAccess(): Unit =
    checkHasErrorAroundCaret(
      s"""
         |object Test {
         |  enum Foo { private case Bar }
         |  val a = Foo.Ba${CARET}r
         |}
         |""".stripMargin
    )

  def testCaseConformsToBaseType(): Unit =
    checkTextHasNoErrors(
      """
        |object Test {
        |  enum Foo { case Bar; case Baz(x: Int); case Qux extends Foo }
        |  val a: Foo = Foo.Bar
        |  val b: Foo = Foo.Baz(123)
        |  val c: Foo = Foo.Qux
        |}
        |""".stripMargin
    )

  def testConformsToSuperTypes(): Unit =
    checkTextHasNoErrors(
      """
        |object Test {
        |  trait F
        |  trait G
        |  enum Foo {
        |    case Bar(x: Int) extends Foo with F with G
        |    case Baz         extends Foo with G with F
        |  }
        |
        |  val f: F = Foo.Bar(1)
        |  val g: G = Foo.Bar(1)
        |  val f2: F = Foo.Baz
        |  val g2: G = Foo.Baz
        |}
        |""".stripMargin
    )

  def testSingletonCasePolyEnum(): Unit =
    checkTextHasNoErrors(
      """
        |object Test {
        |  enum Foo[-T <: String, +U] { case Bar }
        |  val f: Foo[String, Nothing] = Foo.Bar
        |}
        |""".stripMargin
    )

  def testResolveEnumCaseParameters(): Unit =
    checkTextHasNoErrors(
      """
        |enum Foo(x: Int, y: Int):
        |  case Bar(x1: Int, y1: Int) extends Foo(x1, y1)
        |""".stripMargin
    )

  def testSCL19627(): Unit =
    checkHasErrorAroundCaret(
      s"""
         |enum Foo(x: Int):
         |  case Bar extends Foo(${CARET}x)
         |
         |""".stripMargin
    )

  def testEnumConstructorParameters(): Unit =
    checkTextHasNoErrors(
      """
        |enum Foo(x: Int):
        |  def foo: Int = x
        |  case Bar
        |
        |""".stripMargin
    )

  def testSCL19628(): Unit =
    checkTextHasNoErrors(
      """
        |enum ExampleEnum(p1: Int, p2: String):
        |  def this(p1: Int) = this(p1, "p2")
        |  case SomeEnumCase extends ExampleEnum(1, "p2")
        |  case AnotherEnumCase(p1: Int) extends ExampleEnum(p1)
        |""".stripMargin
    )

  def testSCL21138(): Unit = doResolveTest(
    s"""
       |enum Foo {
       |  case Bar
       |
       |  def bar = this match {
       |    case B${REFSRC}ar /*Foo is unresolved*/ => ???
       |  }
       |}
       |""".stripMargin
  )

  def testSCL21269(): Unit = checkTextHasNoErrors(
    """
      |enum MyEnum extends java.lang.Enum[MyEnum] {
      |  case Foo
      |}
      |""".stripMargin
  )

  def testCreateBaseClassInstance(): Unit = {
  //@TODO: prohibit extending from enum class
//    checkHasErrorAroundCaret(
//      s"""
//         |object Test {
//         |  enum Foo { case Bar }
//         |  val f: Foo = new F${CARET}oo {}
//         |}
//         |""".stripMargin
//    )
  }

  def testSCL20882(): Unit = checkTextHasNoErrors(
    """
      |object example1:
      |  object obj {
      |    enum MyColor:
      |      case red2, green2
      |    MyColor.red2
      |  }
      |
      |//Enum inside function
      |object example2:
      |  def foo(): Unit = {
      |    enum MyColor:
      |      case red3, green3
      |    MyColor.red3
      |  }
      |
      |//Enum inside local scope
      |object example3:
      |  {
      |    enum MyColor:
      |      case red4, green4
      |    MyColor.red4
      |  }
      |""".stripMargin
  )

  def testSCL21388(): Unit = checkTextHasNoErrors(
    """enum Color { case Green }
      |object Color
      |
      |object Test { Color.values }
      |""".stripMargin
  )

  def testSCL21397(): Unit = checkHasErrorAroundCaret(
    s"""
      |object Scope {
      |  private enum Color { case Green }
      |}
      |type T = Scope.Co${CARET}lor
      |""".stripMargin
  )
}

