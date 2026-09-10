package org.jetbrains.plugins.scala.codeInsight.hints

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.codeInsight.InlayHintsTestBase
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHints
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}

class TypeArgumentsHintTest extends InlayHintsTestBase {
  import Hint.{End => E, Start => S}

  override def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  private def doTest(text: String): Unit = {
    val old = ScalaApplicationSettings.XRAY_SHOW_TYPE_ARGUMENT_HINTS
    try {
      ScalaHintsSettings.xRayMode = true
      ScalaApplicationSettings.XRAY_SHOW_TYPE_ARGUMENT_HINTS = true
      doInlayTest(text)
    } finally {
      ScalaHintsSettings.xRayMode = false
      ScalaApplicationSettings.XRAY_SHOW_TYPE_ARGUMENT_HINTS = old
    }
  }

  /** X-Ray mode also turns on the implicit argument hints, see `ScalaEditorFactoryListener.setXRayModeEnabled` */
  private def doTestWithImplicitHints(text: String): Unit = {
    val old = ImplicitHints.enabled
    try {
      ImplicitHints.enabled = true
      doTest(text)
    } finally {
      ImplicitHints.enabled = old
    }
  }

  def testSimple(): Unit = doTest(
    s"""
       |def test[T](t: T): T = t
       |
       |test$S[Int]$E(1)
       |""".stripMargin
  )

  def testMultipleArgumentLists(): Unit = doTest(
    s"""
       |def test[T, S](t: T)(s: S): Unit = ()
       |
       |test$S[Int, String]$E(1)("str")
       |""".stripMargin
  )

  def testInterleavedArgumentLists(): Unit = doTest(
    s"""
       |def test[T](t: T)[S](s: S): Unit = ()
       |
       |test$S[Int]$E(1)$S[String]$E("str")
       |""".stripMargin
  )

  def testTypeParameterBounds(): Unit = doTest(
    s"""
       |trait Base
       |trait Derived extends Base
       |
       |def test[A >: Base](a: A): A = ???
       |
       |val z: Base = test$S[Base]$E(new Derived {})
       |val z2$S: Base$E = test$S[Base]$E(new Derived {})
       |""".stripMargin
  )

  def testNamedTypeArgumentAndInferredTypeArgument(): Unit = doTest(
    s"""
       |import scala.language.experimental.namedTypeArguments
       |
       |def test[T, S](t: T)(s: S): Unit = ()
       |
       |test[T = Int$S, S = String$E](1)("str")
       |""".stripMargin
  )

  def testNamedTypeArgumentAndInferredInterleavedTypeArgument(): Unit = doTest(
    s"""
       |import scala.language.experimental.namedTypeArguments
       |
       |def test[T](t: T)[S](s: S): Unit = ()
       |
       |test[T = Int](1)$S[String]$E("str")
       |""".stripMargin
  )

  def testNamedTypeArgumentAndInferredTypeArgumentInInterleavedClause(): Unit = doTest(
    s"""
       |import scala.language.experimental.namedTypeArguments
       |
       |def test[T](t: T)[S, R](s: S, r: R): Unit = ()
       |
       |test[T = Int](1)[S = String$S, R = Boolean$E]("str", true)
       |""".stripMargin
  )

  def testApply(): Unit = doTest(
    s"""
       |object Test {
       |  def apply[T](t: T): T = t
       |}
       |
       |Test$S.apply$E$S[Int]$E(3)
       |""".stripMargin
  )

  def testNotInferred(): Unit = doTest(
    s"""
       |trait A
       |trait B extends A
       |
       |def test[T >: B](t: T): T = t
       |
       |test$S[Any]$E()
       |""".stripMargin
  )

  def testInfix(): Unit = doTest(
    s"""object Test {
       |  def test[T](t: T): T = t
       |}
       |
       |Test test$S[Int]$E 1
       |""".stripMargin
  )

  def testApplyInTheMiddle(): Unit = doTest(
    s"""
       |class Ret {
       |  def apply[T, S](t: T)(s: S): Unit = t
       |}
       |
       |def test[T, S](t: T)(s: S): Ret = ???
       |
       |test$S[Int, String]$E(1)("str")$S.apply$E$S[Boolean, Double]$E(true)(1.0)
       |
       |""".stripMargin
  )

  def testConstructor(): Unit = doTest(
    s"""
       |class Test[T, S](t: T, s: S)
       |
       |new Test$S[Int, String]$E(1, "str")
       |
       |""".stripMargin
  )

  def testPolymorphicLambda(): Unit = doTest(
    s"""
       |class Ret {
       |  def apply[T, S](t: T)(s: S): Unit = t
       |}
       |
       |val x$S: PolyFunction{def apply[T](i: T): Ret}$E = [T] => (i: T) => new Ret
       |x$S.apply$E$S[Boolean]$E(true)$S.apply$E$S[String, Int]$E("blub")(3)
       |""".stripMargin
  )

  def testParameterlessMethod(): Unit = doTest(
    s"""
       |def bar[A]: A = ???
       |
       |val v: Int = bar$S[Int]$E
       |""".stripMargin
  )

  def testParameterlessMethodInsideExpectedType(): Unit = doTest(
    s"""
       |def bar[A]: List[A] = ???
       |
       |val v: List[Int] = bar$S[Int]$E
       |""".stripMargin
  )

  def testParameterlessMethodAsArgument(): Unit = doTest(
    s"""
       |def bar[A]: A = ???
       |def foo(s: String): Unit = ()
       |
       |foo(bar$S[String]$E)
       |""".stripMargin
  )

  def testParameterlessMethodWithoutExpectedType(): Unit = doTest(
    s"""
       |def bar[A]: A = ???
       |
       |bar$S[Nothing]$E
       |bar$S[Nothing]$E.toString
       |""".stripMargin
  )

  def testParameterlessMethodWithExplicitTypeArgument(): Unit = doTest(
    s"""
       |def bar[A]: A = ???
       |
       |val v: Int = bar[Int]
       |""".stripMargin
  )

  def testParameterlessMethodWithUndeterminedTypeArgument(): Unit = doTest(
    s"""
       |def bar[A, B]: A = ???
       |
       |val v: Int = bar$S[Int, Nothing]$E
       |""".stripMargin
  )

  def testParameterlessMethodWithLowerBound(): Unit = doTest(
    s"""
       |class Blub[T] { def test[TT >: T]: TT = ??? }
       |
       |Blub[Int].test$S[Int]$E
       |Blub[Int]().test$S[Int]$E
       |new Blub[Int].test$S[Int]$E
       |""".stripMargin
  )

  def testConstructorWithoutArgumentList(): Unit = doTest(
    s"""
       |class Example[A]
       |
       |val x: Example[Int] = new Example$S[Int]$E
       |""".stripMargin
  )

  def testConstructorWithEmptyArgumentList(): Unit = doTest(
    s"""
       |class Example[A]()
       |
       |val x: Example[Int] = new Example$S[Int]$E()
       |""".stripMargin
  )

  def testConstructorWithExplicitTypeArguments(): Unit = doTest(
    s"""
       |class Example[A](a: A)
       |
       |val x: Example[Int] = new Example[Int](1)
       |""".stripMargin
  )

  def testConstructorWithoutExpectedType(): Unit = doTest(
    s"""
       |class Example[A]
       |
       |new Example$S[Nothing]$E
       |""".stripMargin
  )

  def testUniversalApplyWithoutArguments(): Unit = doTest(
    s"""
       |class Example[A]()
       |
       |val x: Example[Int] = Example$S[Int]$E()
       |""".stripMargin
  )

  def testUniversalApplyWithExplicitTypeArguments(): Unit = doTest(
    s"""
       |class Example[A]()
       |
       |val x: Example[Int] = Example[Int]()
       |""".stripMargin
  )

  def testParameterlessMethodWithGivenInScope(): Unit = doTest(
    s"""
       |def get[A]: A = ???
       |given Int = 3
       |
       |val x: Int = get$S[Int]$E
       |""".stripMargin
  )

  def testMethodWithOnlyUsingClause(): Unit = doTest(
    s"""
       |def get[A](using a: A): A = ???
       |given Int = 3
       |
       |val x: Int = get$S[Int]$E
       |""".stripMargin
  )

  def testMethodWithOnlyImplicitClause(): Unit = doTest(
    s"""
       |def get[A](implicit a: A): A = ???
       |implicit val i: Int = 3
       |
       |val x: Int = get$S[Int]$E
       |""".stripMargin
  )

  def testTypeArgumentInferredFromUsingArgumentOnly(): Unit = doTest(
    s"""
       |trait Show[A]
       |given Show[Int] = ???
       |
       |def show[A](using s: Show[A]): Unit = ()
       |
       |val x: Unit = show$S[Int]$E
       |""".stripMargin
  )

  def testTypeArgumentInferredFromUsingArgumentAfterExplicitClause(): Unit = doTest(
    s"""
       |def get[A](i: Int)(using a: A): A = ???
       |given Int = 3
       |
       |val x: Int = get$S[Int]$E(1)
       |""".stripMargin
  )

  def testExplicitUsingArgument(): Unit = doTest(
    s"""
       |def get[A](using a: A): A = ???
       |
       |val x: Int = get$S[Int]$E(using 3)
       |""".stripMargin
  )

  // `(...)` is the hint for the implicit argument that could not be found
  def testTypeArgumentWithMissingUsingArgument(): Unit = doTest(
    s"""
       |trait Show[A]
       |
       |def show[A](i: Int)(using s: Show[A]): Unit = ()
       |
       |show$S[Nothing]$E(1)$S(...)$E
       |""".stripMargin
  )

  def testTypeArgumentAndImplicitArgumentHintOfUsingClause(): Unit = doTestWithImplicitHints(
    s"""
       |def get[A](using a: A): A = ???
       |given Int = 3
       |
       |val x: Int = get$S[Int]$E$S(given_Int)$E
       |""".stripMargin
  )

  def testTypeArgumentAndImplicitArgumentHintAfterExplicitClause(): Unit = doTestWithImplicitHints(
    s"""
       |def get[A](i: Int)(using a: A): A = ???
       |given Int = 3
       |
       |val x: Int = get$S[Int]$E(1)$S(given_Int)$E
       |""".stripMargin
  )

  def testConstructorWithUsingClause(): Unit = doTestWithImplicitHints(
    s"""
       |class Box[A](using a: A)
       |given Int = 3
       |
       |val x: Box[Int] = new Box$S[Int]$E$S(given_Int)$E
       |""".stripMargin
  )

  def testConstructorWithExplicitArgumentAndUsingClause(): Unit = doTestWithImplicitHints(
    s"""
       |trait Show[A]
       |given Show[Int] = ???
       |
       |class Box[A](a: A)(using s: Show[A])
       |
       |val x$S: Box[Int]$E = new Box$S[Int]$E(1)$S(given_Show_Int)$E
       |""".stripMargin
  )

  def testConstructorWithTypeArgumentInferredFromUsingArgument(): Unit = doTestWithImplicitHints(
    s"""
       |trait Show[A]
       |given Show[Int] = ???
       |
       |class Box[A]()(using s: Show[A])
       |
       |val x$S: Box[Int]$E = new Box$S[Int]$E()$S(given_Show_Int)$E
       |""".stripMargin
  )

  def testUniversalApplyWithExplicitArgumentAndUsingClause(): Unit = doTestWithImplicitHints(
    s"""
       |trait Show[A]
       |given Show[Int] = ???
       |
       |class Box[A](a: A)(using s: Show[A])
       |
       |val x$S: Box[Int]$E = Box$S[Int]$E(1)$S(given_Show_Int)$E
       |""".stripMargin
  )

  def testUniversalApplyWithUsingClause(): Unit = doTestWithImplicitHints(
    s"""
       |class Box[A]()(using a: A)
       |given Int = 3
       |
       |val x: Box[Int] = Box$S[Int]$E()$S(given_Int)$E
       |""".stripMargin
  )

  def testApplyMethodWithUsingClause(): Unit = doTestWithImplicitHints(
    s"""
       |object O {
       |  def apply[A](using a: A): A = ???
       |}
       |given Int = 3
       |
       |val x: Int = O.apply$S[Int]$E$S(given_Int)$E
       |""".stripMargin
  )

  def testInsertedApplyMethodWithUsingClause(): Unit = doTestWithImplicitHints(
    s"""
       |object O {
       |  def apply[A](i: Int)(using a: A): A = ???
       |}
       |given Int = 3
       |
       |val x: Int = O$S.apply$E$S[Int]$E(1)$S(given_Int)$E
       |""".stripMargin
  )

  def testTypeArgumentsFromExplicitAndFromUsingArgument(): Unit = doTestWithImplicitHints(
    s"""
       |trait Show[A]
       |given Show[Int] = ???
       |
       |def show[A, B](b: B)(using s: Show[A]): Unit = ()
       |
       |show$S[Int, String]$E("str")$S(given_Show_Int)$E
       |""".stripMargin
  )

  def testInterleavedArgumentListsWithUsingClause(): Unit = doTestWithImplicitHints(
    s"""
       |trait Show[A]
       |given Show[Int] = ???
       |
       |def show[A](a: A)[B](b: B)(using s: Show[A]): Unit = ()
       |
       |show$S[Int]$E(1)$S[String]$E("str")$S(given_Show_Int)$E
       |""".stripMargin
  )

  def testInfixWithUsingClause(): Unit = doTestWithImplicitHints(
    s"""
       |trait Show[A]
       |given Show[Int] = ???
       |
       |object O {
       |  def show[A](a: A)(using s: Show[A]): Unit = ()
       |}
       |
       |O show$S[Int]$E 1$S(given_Show_Int)$E
       |""".stripMargin
  )

  def testExtensionMethodWithUsingClause(): Unit = doTestWithImplicitHints(
    s"""
       |trait Show[A]
       |given Show[Int] = ???
       |
       |extension [A](a: A) def show(using s: Show[A]): Unit = ()
       |
       |1.show$S(given_Show_Int)$E
       |""".stripMargin
  )

  def testTypeClauseAfterUsingClause(): Unit = doTestWithImplicitHints(
    s"""
       |def get[A](using A)[B <: A](using B): A = ???
       |given Int = 3
       |
       |val x: Int = get[Int]$S(given_Int)$E$S[Int]$E$S(given_Int)$E
       |""".stripMargin
  )

  def testUndeterminedTypeClauseAfterUsingClause(): Unit = doTestWithImplicitHints(
    s"""
       |def get[A](using A)[B]: A = ???
       |given Int = 3
       |
       |val x: Int = get[Int]$S(given_Int)$E$S[Nothing]$E
       |""".stripMargin
  )

  def testTypeClauseAfterTermClause(): Unit = doTestWithImplicitHints(
    s"""
       |def get[A](x: A)[B <: A](using y: B): A = ???
       |given Int = 3
       |
       |val x: Int = get$S[Int]$E(1)$S[Int]$E$S(given_Int)$E
       |""".stripMargin
  )

  def testTypeClauseAfterTermClauseWithExplicitTypeArguments(): Unit = doTestWithImplicitHints(
    s"""
       |def get[A](x: A)[B <: A](using y: B): A = ???
       |given Int = 3
       |
       |val x: Int = get[Int](1)[Int]$S(given_Int)$E
       |""".stripMargin
  )

  def testExplicitUsingArgumentAfterTypeClause(): Unit = doTestWithImplicitHints(
    s"""
       |def get[A](x: A)[B <: A](using y: B): A = ???
       |given Int = 3
       |
       |val x: Int = get$S[Int]$E(1)$S[Int]$E(using 3)
       |""".stripMargin
  )

  def testExplicitUsingArgumentAndExplicitTypeArguments(): Unit = doTestWithImplicitHints(
    s"""
       |def get[A](x: A)[B <: A](using y: B): A = ???
       |given Int = 3
       |
       |val x: Int = get[Int](1)[Int](using 3)
       |""".stripMargin
  )

  def testExplicitUsingArgumentAndExplicitTypeArgumentsOfTypeClause(): Unit = doTestWithImplicitHints(
    s"""
       |def get[A](x: A)[B <: A](using y: B): A = ???
       |given Int = 3
       |
       |val x: Int = get$S[Int]$E(1)[Int](using 3)
       |""".stripMargin
  )

  def testTypeClausesAroundUsingClauses(): Unit = doTestWithImplicitHints(
    s"""
       |def get[A](using x: A)[B <: A](using y: B): A = ???
       |given Int = 3
       |
       |val x: Int = get[Int]$S(given_Int)$E[Int]$S(given_Int)$E
       |""".stripMargin
  )

  def testTypeClauseBetweenUsingClauses(): Unit = doTestWithImplicitHints(
    s"""
       |def get[A](using x: A)[B <: A](using y: B): A = ???
       |given Int = 3
       |
       |val x: Int = get[Int]$S(given_Int)$E$S[Int]$E$S(given_Int)$E
       |""".stripMargin
  )

  def testLeadingUsingClauseOfGenericCall(): Unit = doTestWithImplicitHints(
    s"""
       |trait Show[A]
       |given Show[Int] = ???
       |
       |def show(using s: Show[Int])[B]: B = ???
       |
       |val x: String = show$S(given_Show_Int)$E[String]
       |""".stripMargin
  )

  def testFactoryAndApplyAtSameAnchor(): Unit = doTestWithImplicitHints(
    s"""
       |class Callable {
       |  def apply[A](a: A)(using String): A = a
       |}
       |given Int = 1
       |given String = "context"
       |def factory[A]()(using Int): Callable = ???
       |
       |factory$S[Nothing]$E()$S(given_Int)$E$S.apply$E$S[Boolean]$E(true)$S(given_String)$E
       |""".stripMargin
  )

  def testUsingClausesBeforeApplyOnResult(): Unit = doTestWithImplicitHints(
    s"""
       |class X {
       |  def apply(s: String): Int = 3
       |}
       |def get[A](using x: A)[B <: A](using y: B): X = ???
       |given Int = 3
       |
       |get[Int]$S(given_Int)$E$S[Int]$E$S(given_Int)$E$S.apply$E("hello")
       |""".stripMargin
  )

  def testUsingClausesOnBothFactoryAndApply(): Unit = doTestWithImplicitHints(
    s"""
       |class X {
       |  def apply(s: String)(using Boolean): Int = 3
       |}
       |def get[A](using x: A)[B <: A](using y: B): X = ???
       |given Int = 3
       |given Boolean = true
       |
       |get[Int]$S(given_Int)$E$S[Int]$E$S(given_Int)$E$S.apply$E("hello")$S(given_Boolean)$E
       |""".stripMargin
  )

  def testUsingClauseBeforeApplyWithoutTypeArguments(): Unit = doTestWithImplicitHints(
    s"""
       |class X {
       |  def apply(s: String)(using Boolean): Int = 3
       |}
       |def get(using Int): X = ???
       |given Int = 3
       |given Boolean = true
       |
       |get$S(given_Int)$E$S.apply$E("hello")$S(given_Boolean)$E
       |""".stripMargin
  )

  def testEtaExpandedMethod(): Unit = doTestWithImplicitHints(
    s"""
       |given String = "context"
       |def method[A](a: A)(using String): A = a
       |
       |val function: Int => Int = method$S[Int]$E$S(given_String)$E
       |""".stripMargin
  )

  def testParenthesisedCallWithUsingClause(): Unit = doTestWithImplicitHints(
    s"""
       |given String = "context"
       |def method[A](a: A)(using String): A = a
       |
       |(method)$S[Int]$E(1)$S(given_String)$E
       |""".stripMargin
  )
}
