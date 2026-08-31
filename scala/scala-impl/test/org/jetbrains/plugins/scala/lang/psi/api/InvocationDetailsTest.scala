package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.InvocationDetails._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.base.ConstructorInvocationLike
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScValueOrVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.{Context, TypePresentationContext}
import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category


/**
 * What [[InvocationDetails]] reports for the last statement of the file. Every expression of that
 * statement is listed, outermost first, with the call it is or with `no call` if it is none, since
 * the syntax of one call spans several expressions and only the outermost of them is that call.
 *
 * A call is rendered as the properties it has, its target and its this-expression, followed by the
 * clauses of the callee in the order of its signature, each with the arguments it is applied to,
 * whether the source writes them out, and the element they follow. A type argument the source does
 * not write out is marked with a question mark, so `[T = ?Int]` is inferred and `[T = Int]` is
 * written out.
 *
 * A call that names its callee and applies it to all of its clauses has none of the properties, so
 * it is rendered without a `props` line at all.
 */
@Category(Array(classOf[TypecheckerTests]))
abstract class InvocationDetailsTestBase extends ScalaLightCodeInsightFixtureTestCase {
  //-----------------------------------------------------------------------------------------------
  // value clauses
  //-----------------------------------------------------------------------------------------------

  def testMethodWithoutParameters(): Unit = doTest(
    """
      |def f: Int = 1
      |
      |f
      |""",
    """
      |f
      |  target:   f
      |"""
  )

  def testEmptyArgumentList(): Unit = doTest(
    """
      |def f(): Int = 1
      |
      |f()
      |""",
    """
      |f()
      |  target:   f
      |  () (explicit, after `f`)
      |f
      |  no call
      |"""
  )

  def testSingleClause(): Unit = doTest(
    """
      |def f(i: Int): Int = i
      |
      |f(1)
      |""",
    """
      |f(1)
      |  target:   f
      |  (i = 1) (explicit, after `f`)
      |f
      |  no call
      |"""
  )

  def testMultipleClauses(): Unit = doTest(
    """
      |def f(i: Int)(s: String): Unit = ()
      |
      |f(1)("s")
      |""",
    """
      |f(1)("s")
      |  target:   f
      |  (i = 1) (explicit, after `f`)
      |  (s = "s") (explicit, after `f(1)`)
      |f(1)
      |  no call
      |f
      |  no call
      |"""
  )

  def testThreeClauses(): Unit = doTest(
    """
      |def f(i: Int)(s: String)(b: Boolean): Unit = ()
      |
      |f(1)("s")(true)
      |""",
    """
      |f(1)("s")(true)
      |  target:   f
      |  (i = 1) (explicit, after `f`)
      |  (s = "s") (explicit, after `f(1)`)
      |  (b = true) (explicit, after `f(1)("s")`)
      |f(1)("s")
      |  no call
      |f(1)
      |  no call
      |f
      |  no call
      |"""
  )

  def testNamedArguments(): Unit = doTest(
    """
      |def f(i: Int, s: String): Unit = ()
      |
      |f(s = "s", i = 1)
      |""",
    """
      |f(s = "s", i = 1)
      |  target:   f
      |  (s = "s", i = 1) (explicit, after `f`)
      |f
      |  no call
      |s = "s"
      |  no call
      |s
      |  no call
      |i = 1
      |  no call
      |i
      |  no call
      |"""
  )

  def testDefaultArgument(): Unit = doTest(
    """
      |def f(i: Int, s: String = "x"): Unit = ()
      |
      |f(1)
      |""",
    """
      |f(1)
      |  target:   f
      |  (i = 1, s = "x") (explicit, after `f`)
      |f
      |  no call
      |"""
  )

  def testRepeatedParameter(): Unit = doTest(
    """
      |def f(is: Int*): Unit = ()
      |
      |f(1, 2)
      |""",
    """
      |f(1, 2)
      |  target:   f
      |  (is = 1, is = 2) (explicit, after `f`)
      |f
      |  no call
      |"""
  )

  //-----------------------------------------------------------------------------------------------
  // auto-tupling is a property of an individual value clause
  //-----------------------------------------------------------------------------------------------

  def testAutoTupling(): Unit = doTest(
    """
      |def f(pair: (Int, Int)): Unit = ()
      |
      |f(1, 2)
      |""",
    """
      |f(1, 2)
      |  target:   f
      |  (pair = 1, pair = 2) (explicit, auto tupled, after `f`)
      |f
      |  no call
      |"""
  )

  def testAutoTuplingInfersATupleType(): Unit = doTest(
    """
      |def f[T](value: T): Unit = ()
      |
      |f(1, 2)
      |""",
    """
      |f(1, 2)
      |  target:   f
      |  [T = ?(Int, Int)] (inferred, after `f`)
      |  (value = 1, value = 2) (explicit, auto tupled, after `f`)
      |f
      |  no call
      |"""
  )

  def testExplicitTupleIsNotAutoTupling(): Unit = doTest(
    """
      |def f(pair: (Int, Int)): Unit = ()
      |
      |f((1, 2))
      |""",
    """
      |f((1, 2))
      |  target:   f
      |  (pair = (1, 2)) (explicit, after `f`)
      |f
      |  no call
      |"""
  )

  def testMultipleParametersAreNotAutoTupling(): Unit = doTest(
    """
      |def f(a: Int, b: Int): Unit = ()
      |
      |f(1, 2)
      |""",
    """
      |f(1, 2)
      |  target:   f
      |  (a = 1, b = 2) (explicit, after `f`)
      |f
      |  no call
      |"""
  )

  def testRepeatedParameterIsNotAutoTupling(): Unit = doTest(
    """
      |def f(args: Int*): Unit = ()
      |
      |f(1, 2)
      |""",
    """
      |f(1, 2)
      |  target:   f
      |  (args = 1, args = 2) (explicit, after `f`)
      |f
      |  no call
      |"""
  )

  def testInapplicableArgumentsAreNotAutoTupling(): Unit = doTest(
    """
      |def f(a: Int): Unit = ()
      |
      |f(1, 2)
      |""",
    """
      |f(1, 2)
      |  target:   f
      |  () (explicit, after `f`)
      |f
      |  no call
      |"""
  )

  def testAutoTuplingOfAnInfixCall(): Unit = doTest(
    """
      |object O { def f(pair: (Int, Int)): Unit = () }
      |
      |O f (1, 2)
      |""",
    """
      |O f (1, 2)
      |  target:   f
      |  thisExpr: O
      |  (pair = 1, pair = 2) (explicit, auto tupled, after `f`)
      |O
      |  no call
      |f
      |  no call
      |"""
  )

  def testAutoTuplingOfAnApplyCall(): Unit = doTest(
    """
      |object O { def apply(pair: (Int, Int)): Unit = () }
      |
      |O(1, 2)
      |""",
    """
      |O(1, 2)
      |  props:    apply
      |  target:   apply
      |  thisExpr: O
      |  (pair = 1, pair = 2) (explicit, auto tupled, after `O`)
      |O
      |  no call
      |"""
  )

  def testAutoTuplingPreservesArgumentExpressions(): Unit = doTest(
    """
      |def f(pair: (Int, Int)): Unit = ()
      |val left = 1
      |val right = 2
      |
      |f(left, right)
      |""",
    """
      |f(left, right)
      |  target:   f
      |  (pair = left, pair = right) (explicit, auto tupled, after `f`)
      |f
      |  no call
      |left
      |  no call
      |right
      |  no call
      |"""
  )

  def testAutoTuplingPreservesTheUpdateArgument(): Unit = doTest(
    """
      |object O { def update(pair: (Int, Int)): Unit = () }
      |
      |O(1) = 2
      |""",
    """
      |O(1) = 2
      |  props:    update
      |  target:   update
      |  thisExpr: O
      |  (pair = 1, pair = 2) (explicit, auto tupled, after `O`)
      |O(1)
      |  no call
      |O
      |  no call
      |"""
  )

  def testAutoTuplingWithAnImplicitClause(): Unit = doTest(
    """
      |def f(pair: (Int, Int))(implicit i: Int): Unit = ()
      |implicit val int: Int = 1
      |f(1, 2)
      |""",
    """
      |f(1, 2)
      |  target:   f
      |  (pair = 1, pair = 2) (explicit, auto tupled, after `f`)
      |  (using int) (inferred, after `f(1, 2)`)
      |f
      |  no call
      |"""
  )

  def testEtaExpandedClauseIsNotAutoTupling(): Unit = doTest(
    """
      |def f(pair: (Int, Int))(a: Int): Unit = ()
      |
      |f(1, 2) _
      |""",
    """
      |f(1, 2) _
      |  props:    partially applied
      |  target:   f
      |  (pair = 1, pair = 2) (explicit, auto tupled, after `f`)
      |  (a: Int) (eta expanded, after `f(1, 2)`)
      |f(1, 2)
      |  no call
      |f
      |  no call
      |"""
  )

  def testAutoAppliedClauseIsNotAutoTupling(): Unit = doTest(
    """
      |new Object
      |""",
    """
      |Object
      |  props:    constructor invocation
      |  target:   Object
      |  () (auto applied, after `Object`)
      |"""
  )

  def testAutoTuplingOfAConstructor(): Unit = doTest(
    """
      |class C(pair: (Int, Int))
      |
      |new C(1, 2)
      |""",
    """
      |C(1, 2)
      |  props:    constructor invocation
      |  target:   C
      |  (pair = 1, pair = 2) (explicit, auto tupled, after `C`)
      |"""
  )

  def testAutoTuplingOfAGenericConstructor(): Unit = doTest(
    """
      |class C[T](value: T)
      |
      |new C(1, 2)
      |""",
    """
      |C(1, 2)
      |  props:    constructor invocation
      |  target:   C
      |  [T = ?(Int, Int)] (inferred, after `C`)
      |  (value = 1, value = 2) (explicit, auto tupled, after `C`)
      |"""
  )

  def testInapplicableConstructorArgumentsAreNotAutoTupling(): Unit = doTest(
    """
      |class C(a: Int)
      |
      |new C(1, 2)
      |""",
    """
      |C(1, 2)
      |  props:    constructor invocation
      |  target:   C
      |  (a = 1, a = 2) (explicit, after `C`)
      |"""
  )

  def testExplicitConstructorTupleIsNotAutoTupling(): Unit = doTest(
    """
      |class C(pair: (Int, Int))
      |
      |new C((1, 2))
      |""",
    """
      |C((1, 2))
      |  props:    constructor invocation
      |  target:   C
      |  (pair = (1, 2)) (explicit, after `C`)
      |"""
  )

  def testRepeatedConstructorParameterIsNotAutoTupling(): Unit = doTest(
    """
      |class C(args: Int*)
      |
      |new C(1, 2)
      |""",
    """
      |C(1, 2)
      |  props:    constructor invocation
      |  target:   C
      |  (args = 1, args = 2) (explicit, after `C`)
      |"""
  )

  def testAutoTuplingOfALaterConstructorClause(): Unit = doTest(
    """
      |class C(a: Int)(pair: (Int, Int))
      |
      |new C(1)(2, 3)
      |""",
    """
      |C(1)(2, 3)
      |  props:    constructor invocation
      |  target:   C
      |  (a = 1) (explicit, after `C`)
      |  (pair = 2, pair = 3) (explicit, auto tupled, after `(1)`)
      |"""
  )

  def testAutoTuplingOfTheFirstConstructorClauseOnly(): Unit = doTest(
    """
      |class C(pair: (Int, Int))(a: Int)
      |
      |new C(1, 2)(3)
      |""",
    """
      |C(1, 2)(3)
      |  props:    constructor invocation
      |  target:   C
      |  (pair = 1, pair = 2) (explicit, auto tupled, after `C`)
      |  (a = 3) (explicit, after `(1, 2)`)
      |"""
  )

  def testMissingArgument(): Unit = doTest(
    """
      |def f(i: Int, s: String): Unit = ()
      |
      |f(1)
      |""",
    """
      |f(1)
      |  target:   f
      |  () (explicit, after `f`)
      |f
      |  no call
      |"""
  )

  def testUnresolvedCall(): Unit = doTest(
    """
      |noSuchMethod(1)()
      |""",
    """
      |noSuchMethod(1)()
      |  props:    apply
      |  target:   <unresolved>
      |  thisExpr: noSuchMethod(1)
      |noSuchMethod(1)
      |  target:   <unresolved>
      |noSuchMethod
      |  no call
      |"""
  )

  def testExplicitApply(): Unit = doTest(
    """
      |object Test {
      |  def apply(): Unit = ()
      |}
      |
      |Test.apply()
      |""".stripMargin,
    """
      |Test.apply()
      |  target:   apply
      |  thisExpr: Test
      |  () (explicit, after `Test.apply`)
      |Test.apply
      |  no call
      |Test
      |  no call
      |""".stripMargin
  )

  //-----------------------------------------------------------------------------------------------
  // type clauses
  //-----------------------------------------------------------------------------------------------

  def testInferredTypeArgument(): Unit = doTest(
    """
      |def f[T](t: T): T = t
      |
      |f(1)
      |""",
    """
      |f(1)
      |  target:   f
      |  [T = ?Int] (inferred, after `f`)
      |  (t = 1) (explicit, after `f`)
      |f
      |  no call
      |"""
  )

  def testExplicitTypeArgument(): Unit = doTest(
    """
      |def f[T](t: T): T = t
      |
      |f[Int](1)
      |""",
    """
      |f[Int](1)
      |  target:   f
      |  [T = Int] (explicit, after `f`)
      |  (t = 1) (explicit, after `f[Int]`)
      |f[Int]
      |  no call
      |f
      |  no call
      |"""
  )

  def testTypeArgumentInferredAsNothing(): Unit = doTest(
    """
      |def f[T](value: Option[T]): Option[T] = value
      |
      |f(None)
      |""",
    """
      |f(None)
      |  target:   f
      |  [T = ?Nothing] (inferred, after `f`)
      |  (value = None) (explicit, after `f`)
      |f
      |  no call
      |None
      |  no call
      |"""
  )

  def testUnconstrainedTypeArgumentWithExpectedType(): Unit = doTest(
    """
      |def f[A, B](value: B): Either[A, B] = ???
      |
      |val result: Either[String, Int] = f(1)
      |""",
    """
      |f(1)
      |  target:   f
      |  [A = ?Nothing, B = ?Int] (inferred, after `f`)
      |  (value = 1) (explicit, after `f`)
      |f
      |  no call
      |"""
  )

  def testTwoTypeParameters(): Unit = doTest(
    """
      |def f[T, S](t: T)(s: S): Unit = ()
      |
      |f(1)("s")
      |""",
    """
      |f(1)("s")
      |  target:   f
      |  [T = ?Int, S = ?String] (inferred, after `f`)
      |  (t = 1) (explicit, after `f`)
      |  (s = "s") (explicit, after `f(1)`)
      |f(1)
      |  no call
      |f
      |  no call
      |"""
  )

  def testTypeArgumentFromExpectedType(): Unit = doTest(
    """
      |def f[T]: T = ???
      |
      |val x: Int = f
      |""",
    """
      |f
      |  target:   f
      |  [T = ?Int] (inferred, after `f`)
      |"""
  )

  def testTypeArgumentFromBounds(): Unit = doTest(
    """
      |def f[T, S]: T = ???
      |
      |val x: Int = f
      |""",
    """
      |f
      |  target:   f
      |  [T = ?Int, S = ?Nothing] (inferred, after `f`)
      |"""
  )

  def testTypeArgumentOfLowerBound(): Unit = doTest(
    """
      |trait Base
      |trait Derived extends Base
      |
      |def f[T >: Base](t: T): T = t
      |
      |f(new Derived {})
      |""",
    """
      |f(new Derived {})
      |  target:   f
      |  [T = ?Base] (inferred, after `f`)
      |  (t = new Derived {}) (explicit, after `f`)
      |f
      |  no call
      |Derived
      |  props:    constructor invocation
      |  target:   Derived
      |"""
  )

  /**
   * A recursive call instantiates the callee with the very type parameters it is declared with, so
   * its type arguments are the ones in scope at the call. They are inferred like any other, even
   * though telling them apart from a type parameter no type argument could be inferred for takes
   * knowing that the declaration they belong to encloses the call.
   */
  def testTypeArgumentOfARecursiveCall(): Unit = doTest(
    """
      |def f[T](t: T): Unit = f(t)
      |""",
    """
      |f(t)
      |  target:   f
      |  [T = ?T] (inferred, after `f`)
      |  (t = t) (explicit, after `f`)
      |f
      |  no call
      |t
      |  no call
      |"""
  )

  /** The same, with a type argument that contains the type parameter in scope rather than being it. */
  def testTypeArgumentAroundTheTypeParameterOfARecursiveCall(): Unit = doTest(
    """
      |def f[T](t: T): Unit = f(Option(t))
      |""",
    """
      |f(Option(t))
      |  target:   f
      |  [T = ?Option[T]] (inferred, after `f`)
      |  (t = Option(t)) (explicit, after `f`)
      |f
      |  no call
      |Option(t)
      |  props:    apply
      |  target:   apply
      |  thisExpr: Option
      |  [A = ?T] (inferred, after `Option`)
      |  (x = t) (explicit, after `Option`)
      |Option
      |  no call
      |t
      |  no call
      |"""
  )

  //-----------------------------------------------------------------------------------------------
  // implicit clauses
  //-----------------------------------------------------------------------------------------------

  def testTrailingImplicitClause(): Unit = doTest(
    """
      |def f(i: Int)(implicit s: String): Unit = ()
      |implicit val string: String = "s"
      |
      |f(1)
      |""",
    """
      |f(1)
      |  target:   f
      |  (i = 1) (explicit, after `f`)
      |  (using string) (inferred, after `f(1)`)
      |f
      |  no call
      |"""
  )

  def testUsingImplicitOnly(): Unit = doTest(
    """
      |def f(implicit s: String): Unit = ()
      |implicit val string: String = "s"
      |
      |f
      |""",
    """
      |f
      |  target:   f
      |  (using string) (inferred, after `f`)
      |"""
  )

  def testExplicitImplicitArguments(): Unit = doTest(
    """
      |def f(i: Int)(implicit s: String): Unit = ()
      |
      |f(1)("s")
      |""",
    """
      |f(1)("s")
      |  target:   f
      |  (i = 1) (explicit, after `f`)
      |  (s = "s") (explicit, after `f(1)`)
      |f(1)
      |  no call
      |f
      |  no call
      |"""
  )

  def testNotFoundImplicitArgument(): Unit = doTest(
    """
      |def f(i: Int)(implicit s: String): Unit = ()
      |
      |f(1)
      |""",
    """
      |f(1)
      |  target:   f
      |  (i = 1) (explicit, after `f`)
      |  (using <not found>) (inferred, after `f(1)`)
      |f
      |  no call
      |"""
  )

  def testTypeArgumentFromImplicitArgument(): Unit = doTest(
    """
      |def get[A](implicit a: A): A = ???
      |implicit val y: Int = 3
      |
      |val x: Int = get
      |""",
    """
      |get
      |  target:   get
      |  [A = ?Int] (inferred, after `get`)
      |  (using y) (inferred, after `get`)
      |"""
  )

  def testTypeArgumentFromImplicitArgumentOfTrailingClause(): Unit = doTest(
    """
      |trait Show[A]
      |implicit val show: Show[Int] = ???
      |
      |def show[A](i: Int)(implicit s: Show[A]): Unit = ()
      |
      |show(1)
      |""",
    """
      |show(1)
      |  target:   show
      |  [A = ?Int] (inferred, after `show`)
      |  (i = 1) (explicit, after `show`)
      |  (using show) (inferred, after `show(1)`)
      |show
      |  no call
      |"""
  )

  def testContextBound(): Unit = doTest(
    """
      |def f[A: Ordering](a: A): Unit = ()
      |
      |f(1)
      |""",
    """
      |f(1)
      |  target:   f
      |  [A = ?Int] (inferred, after `f`)
      |  (a = 1) (explicit, after `f`)
      |  (using Int) (inferred, after `f(1)`)
      |f
      |  no call
      |"""
  )

  //-----------------------------------------------------------------------------------------------
  // partial application
  //-----------------------------------------------------------------------------------------------

  def testEtaExpansionOfAMethodWithClauses(): Unit = doTest(
    """
      |def f(i: Int)(j: Int): Unit = ()
      |
      |f
      |""",
    """
      |f
      |  props:    partially applied
      |  target:   f
      |  (i: Int) (eta expanded, after `f`)
      |  (j: Int) (eta expanded, after `f`)
      |"""
  )

  def testPartialApplication(): Unit = doTest(
    """
      |def f(i: Int)(j: Int): Unit = ()
      |
      |f(1)
      |""",
    """
      |f(1)
      |  props:    partially applied
      |  target:   f
      |  (i = 1) (explicit, after `f`)
      |  (j: Int) (eta expanded, after `f(1)`)
      |f
      |  no call
      |"""
  )

  def testFullApplicationIsNotPartiallyApplied(): Unit = doTest(
    """
      |def f(i: Int)(j: Int): Unit = ()
      |
      |f(1)(2)
      |""",
    """
      |f(1)(2)
      |  target:   f
      |  (i = 1) (explicit, after `f`)
      |  (j = 2) (explicit, after `f(1)`)
      |f(1)
      |  no call
      |f
      |  no call
      |"""
  )

  def testParameterlessMethodIsNotPartiallyApplied(): Unit = doTest(
    """
      |def ff = 3
      |
      |ff
      |""",
    """
      |ff
      |  target:   ff
      |"""
  )

  def testEtaExpansionOfAMethodWithAnEmptyClause(): Unit = doTest(
    """
      |def f(): Int = 1
      |
      |val g = f
      |""",
    """
      |f
      |  props:    partially applied
      |  target:   f
      |  () (eta expanded, after `f`)
      |"""
  )

  def testEtaExpansionAppliesItsImplicitClause(): Unit = doTest(
    """
      |def f(j: Int)(implicit i: Int): Int = 3
      |implicit val int: Int = 1
      |
      |val g = f _
      |""",
    """
      |f _
      |  props:    partially applied
      |  target:   f
      |  (j: Int) (eta expanded, after `f`)
      |  (using int) (inferred, after `f`)
      |f
      |  no call
      |"""
  )

  def testEtaExpansionInfersItsTypeArguments(): Unit = doTest(
    """
      |def f[T](t: T)(u: T): Unit = ()
      |
      |val g: Int => Unit = f(1)
      |""",
    """
      |f(1)
      |  props:    partially applied
      |  target:   f
      |  [T = ?Int] (inferred, after `f`)
      |  (t = 1) (explicit, after `f`)
      |  (u: Int) (eta expanded, after `f(1)`)
      |f
      |  no call
      |"""
  )

  def testEtaExpansionInfersTypeArgumentsFromExpectedFunctionType(): Unit = doTest(
    """
      |def f[T](t: T): Option[T] = Some(t)
      |
      |val g: Int => Option[Int] = f
      |""",
    """
      |f
      |  props:    partially applied
      |  target:   f
      |  [T = ?Int] (inferred, after `f`)
      |  (t: Int) (eta expanded, after `f`)
      |"""
  )

  def testEtaExpansionInfersAnEnclosingTypeParameter(): Unit = doTest(
    """
      |def f[T](t: T): Option[T] = Some(t)
      |
      |def g[A]: A => Option[A] = f
      |""",
    """
      |f
      |  props:    partially applied
      |  target:   f
      |  [T = ?A] (inferred, after `f`)
      |  (t: A) (eta expanded, after `f`)
      |"""
  )

  def testEtaExpansionInfersTypeArgumentsWithAnImplicitClause(): Unit = doTest(
    """
      |def f[T](t: T)(u: T)(implicit i: Int): Option[T] = Some(t)
      |implicit val int: Int = 1
      |
      |val g: Int => Int => Option[Int] = f _
      |""",
    """
      |f _
      |  props:    partially applied
      |  target:   f
      |  [T = ?Int] (inferred, after `f`)
      |  (t: Int) (eta expanded, after `f`)
      |  (u: Int) (eta expanded, after `f`)
      |  (using int) (inferred, after `f`)
      |f
      |  no call
      |"""
  )

  //-----------------------------------------------------------------------------------------------
  // partial application the source spells out, with a trailing underscore or with placeholders
  //-----------------------------------------------------------------------------------------------

  def testUnderscoreEtaExpansion(): Unit = doTest(
    """
      |def f(i: Int): Int = i
      |
      |val g = f _
      |""",
    """
      |f _
      |  props:    partially applied
      |  target:   f
      |  (i: Int) (eta expanded, after `f`)
      |f
      |  no call
      |"""
  )

  def testUnderscoreEtaExpansionOfAPartialApplication(): Unit = doTest(
    """
      |def f(i: Int)(j: Int): Unit = ()
      |
      |val g = f(1) _
      |""",
    """
      |f(1) _
      |  props:    partially applied
      |  target:   f
      |  (i = 1) (explicit, after `f`)
      |  (j: Int) (eta expanded, after `f(1)`)
      |f(1)
      |  no call
      |f
      |  no call
      |"""
  )

  def testUnderscoreEtaExpansionWithTypeArguments(): Unit = doTest(
    """
      |def f[T](t: T): Unit = ()
      |
      |val g = f[Int] _
      |""",
    """
      |f[Int] _
      |  props:    partially applied
      |  target:   f
      |  [T = Int] (explicit, after `f`)
      |  (t: Int) (eta expanded, after `f[Int]`)
      |f[Int]
      |  no call
      |f
      |  no call
      |"""
  )

  def testPlaceholderIsPartiallyApplied(): Unit = doTest(
    """
      |def f(i: Int): Int = i
      |
      |val g = f(_)
      |""",
    """
      |f(_)
      |  props:    partially applied
      |  target:   f
      |  (i = _) (explicit, after `f`)
      |f
      |  no call
      |_
      |  no call
      |"""
  )

  def testPlaceholderOfOneOfSeveralArguments(): Unit = doTest(
    """
      |def f(i: Int, j: Int): Int = i
      |
      |val g = f(1, _)
      |""",
    """
      |f(1, _)
      |  props:    partially applied
      |  target:   f
      |  (i = 1, j = _) (explicit, after `f`)
      |f
      |  no call
      |_
      |  no call
      |"""
  )

  def testArgumentThatIsAPlaceholderFunctionIsNotPartiallyApplied(): Unit = doTest(
    """
      |def f(g: Int => Int): Int = g(1)
      |def h(i: Int): Int = i
      |
      |f(h(_))
      |""",
    """
      |f(h(_))
      |  target:   f
      |  (g = h(_)) (explicit, after `f`)
      |f
      |  no call
      |h(_)
      |  props:    partially applied
      |  target:   h
      |  (i = _) (explicit, after `h`)
      |h
      |  no call
      |_
      |  no call
      |"""
  )

  def testInfixPlaceholder(): Unit = doTest(
    """
      |def run(f: (Int, Int) => Int): Int = f(1, 2)
      |
      |run(_ + _)
      |""".stripMargin,
    """
      |run(_ + _)
      |  target:   run
      |  (f = _ + _) (explicit, after `run`)
      |run
      |  no call
      |_ + _
      |  props:    partially applied
      |  target:   +
      |  thisExpr: _
      |  (_) (explicit, after `+`)
      |_
      |  no call
      |+
      |  no call
      |_
      |  no call
      |""".stripMargin
  )

  //-----------------------------------------------------------------------------------------------
  // synthetic functions, the ones the compiler makes up for the primitives, which are no PsiMethod
  //-----------------------------------------------------------------------------------------------

  def testSyntheticOperator(): Unit = doTest(
    """
      |1 + 2
      |""",
    """
      |1 + 2
      |  target:   +
      |  thisExpr: 1
      |  (2) (explicit, after `+`)
      |+
      |  no call
      |"""
  )

  def testSyntheticOperatorWithAPlaceholder(): Unit = doTest(
    """
      |def run(f: Int => Int): Int = f(1)
      |
      |run(1 + _)
      |""",
    """
      |run(1 + _)
      |  target:   run
      |  (f = 1 + _) (explicit, after `run`)
      |run
      |  no call
      |1 + _
      |  props:    partially applied
      |  target:   +
      |  thisExpr: 1
      |  (_) (explicit, after `+`)
      |+
      |  no call
      |_
      |  no call
      |"""
  )

  def testSyntheticEquality(): Unit = doTest(
    """
      |1 == 2
      |""",
    """
      |1 == 2
      |  target:   ==
      |  thisExpr: 1
      |  (2) (explicit, after `==`)
      |==
      |  no call
      |"""
  )

  /**
   * A synthetic function stands for its own type clause as much as for its value one, so the type
   * parameter of `asInstanceOf` is reported even though it has no PSI clause to point at. It is named
   * after what it is, since the compiler makes it up rather than reading it off any source.
   */
  def testSyntheticFunctionWithTypeParameters(): Unit = doTest(
    """
      |val x: Any = 1
      |
      |x.asInstanceOf[String]
      |""",
    """
      |x.asInstanceOf[String]
      |  target:   asInstanceOf
      |  thisExpr: x
      |  [TypeParameterForSyntheticFunction = String] (explicit, after `x.asInstanceOf`)
      |x.asInstanceOf
      |  no call
      |x
      |  no call
      |"""
  )

  def testSyntheticIsInstanceOf(): Unit = doTest(
    """
      |val x: Any = 1
      |
      |x.isInstanceOf[String]
      |""",
    """
      |x.isInstanceOf[String]
      |  target:   isInstanceOf
      |  thisExpr: x
      |  [TypeParameterForSyntheticFunction = String] (explicit, after `x.isInstanceOf`)
      |x.isInstanceOf
      |  no call
      |x
      |  no call
      |"""
  )

  /** `synchronized` is the one synthetic function that has a clause of each kind. */
  def testSyntheticSynchronized(): Unit = doTest(
    """
      |val x = new Object
      |
      |x.synchronized(1)
      |""",
    """
      |x.synchronized(1)
      |  target:   synchronized
      |  thisExpr: x
      |  [TypeParameterForSyntheticFunction = ?Int] (inferred, after `x.synchronized`)
      |  (1) (explicit, after `x.synchronized`)
      |x.synchronized
      |  no call
      |x
      |  no call
      |"""
  )

  //-----------------------------------------------------------------------------------------------
  // expressions that call nothing
  //-----------------------------------------------------------------------------------------------

  def testReferenceToAValueIsNotACall(): Unit = doTest(
    """
      |val x = Seq(1)
      |
      |x
      |""",
    """
      |x
      |  no call
      |"""
  )

  def testReferenceToAnObjectIsNotACall(): Unit = doTest(
    """
      |object O
      |
      |O
      |""",
    """
      |O
      |  no call
      |"""
  )

  def testAssignmentToAVariableIsNotACall(): Unit = doTest(
    """
      |var x: Int = 1
      |
      |x = 2
      |""",
    """
      |x = 2
      |  no call
      |x
      |  no call
      |"""
  )

  def testAssignmentToAFieldIsNotACall(): Unit = doTest(
    """
      |class A {
      |  var x: Int = 1
      |}
      |
      |val a = new A
      |a.x = 2
      |""",
    """
      |a.x = 2
      |  no call
      |a.x
      |  no call
      |a
      |  no call
      |"""
  )

  /**
   * An assignment to something a setter is defined for calls that setter, and the right side of the
   * assignment is the argument of the one clause it takes.
   */
  def testAssignmentCall(): Unit = doTest(
    """
      |class A {
      |  def x: Int = 1
      |  def x_=(i: Int): Unit = ()
      |}
      |
      |val a = new A
      |a.x = 2
      |""",
    """
      |a.x = 2
      |  props:    assignment call
      |  target:   x_=
      |  thisExpr: a
      |  (i = 2) (explicit, after `a.x`)
      |a.x
      |  no call
      |a
      |  no call
      |"""
  )

  /** The right side of an assignment is an argument like any other, so a call of one is its own. */
  def testAssignmentCallOfACall(): Unit = doTest(
    """
      |class A {
      |  def x: Int = 1
      |  def x_=(i: Int): Unit = ()
      |}
      |
      |def f(): Int = 2
      |val a = new A
      |a.x = f()
      |""",
    """
      |a.x = f()
      |  props:    assignment call
      |  target:   x_=
      |  thisExpr: a
      |  (i = f()) (explicit, after `a.x`)
      |a.x
      |  no call
      |a
      |  no call
      |f()
      |  target:   f
      |  () (explicit, after `f`)
      |f
      |  no call
      |"""
  )

  def testNameOfANamedArgumentIsNotACall(): Unit = doTest(
    """
      |def f(i: Int): Unit = ()
      |
      |f(i = 1)
      |""",
    """
      |f(i = 1)
      |  target:   f
      |  (i = 1) (explicit, after `f`)
      |f
      |  no call
      |i = 1
      |  no call
      |i
      |  no call
      |"""
  )

  //-----------------------------------------------------------------------------------------------
  // a clause the callee does not have is applied to the result of the call, which is another call
  //-----------------------------------------------------------------------------------------------

  def testApplyOfAValueWithoutArguments(): Unit = doTest(
    """
      |val x = Seq(1)
      |
      |x()
      |""",
    """
      |x()
      |  props:    apply
      |  target:   apply
      |  thisExpr: x
      |  () (explicit, after `x`)
      |x
      |  no call
      |"""
  )

  def testApplyOfTheResultOfAParameterlessMethod(): Unit = doTest(
    """
      |def f = (i: Int) => 3
      |
      |f(3)
      |""",
    """
      |f(3)
      |  props:    apply
      |  target:   apply
      |  thisExpr: f
      |  (v1 = 3) (explicit, after `f`)
      |f
      |  target:   f
      |"""
  )

  def testTypeArgumentsAppliedToTheResultOfACall(): Unit = doTest(
    """
      |object Test {
      |  def apply[X] = 3
      |}
      |
      |def f() = Test
      |
      |f()[Int]
      |""",
    """
      |f()[Int]
      |  props:    apply
      |  target:   apply
      |  thisExpr: f()
      |  [X = Int] (explicit, after `f()`)
      |f()
      |  target:   f
      |  () (explicit, after `f`)
      |f
      |  no call
      |"""
  )

  def testApplyInTheMiddleOfACall(): Unit = doTest(
    """
      |class Ret {
      |  def apply[S](s: S): Unit = ()
      |}
      |
      |def f[T](t: T): Ret = ???
      |
      |f(1)("s")
      |""",
    """
      |f(1)("s")
      |  props:    apply
      |  target:   apply
      |  thisExpr: f(1)
      |  [S = ?String] (inferred, after `f(1)`)
      |  (s = "s") (explicit, after `f(1)`)
      |f(1)
      |  target:   f
      |  [T = ?Int] (inferred, after `f`)
      |  (t = 1) (explicit, after `f`)
      |f
      |  no call
      |"""
  )


  //-----------------------------------------------------------------------------------------------
  // the syntactic forms of a call
  //-----------------------------------------------------------------------------------------------

  def testQualifiedCall(): Unit = doTest(
    """
      |object O {
      |  def f[T](t: T): T = t
      |}
      |
      |O.f(1)
      |""",
    """
      |O.f(1)
      |  target:   f
      |  thisExpr: O
      |  [T = ?Int] (inferred, after `O.f`)
      |  (t = 1) (explicit, after `O.f`)
      |O.f
      |  no call
      |O
      |  no call
      |"""
  )

  def testInfixCall(): Unit = doTest(
    """
      |object O {
      |  def f[T](t: T): T = t
      |}
      |
      |O f 1
      |""",
    """
      |O f 1
      |  target:   f
      |  thisExpr: O
      |  [T = ?Int] (inferred, after `f`)
      |  (t = 1) (explicit, after `f`)
      |O
      |  no call
      |f
      |  no call
      |"""
  )

  def testInfixCallWithMoreClauses(): Unit = doTest(
    """
      |object O {
      |  def f[T](t: T)(i: Int): T = t
      |}
      |
      |(O f 1)(2)
      |""",
    """
      |(O f 1)(2)
      |  target:   f
      |  thisExpr: O
      |  [T = ?Int] (inferred, after `f`)
      |  (t = 1) (explicit, after `f`)
      |  (i = 2) (explicit, after `(O f 1)`)
      |O f 1
      |  no call
      |O
      |  no call
      |f
      |  no call
      |"""
  )

  def testPrefixCall(): Unit = doTest(
    """
      |class A {
      |  def unary_! : Int = 1
      |}
      |
      |!(new A)
      |""",
    """
      |!(new A)
      |  target:   unary_!
      |  thisExpr: (new A)
      |!
      |  no call
      |A
      |  props:    constructor invocation
      |  target:   A
      |  () (auto applied, after `A`)
      |"""
  )

  def testPostfixCall(): Unit = doTest(
    """
      |import scala.language.postfixOps
      |
      |object O {
      |  def f: Int = 1
      |}
      |
      |O f
      |""",
    """
      |O f
      |  target:   f
      |  thisExpr: O
      |O
      |  no call
      |f
      |  no call
      |"""
  )

  def testParenthesisedInvokedExpression(): Unit = doTest(
    """
      |object O {
      |  def f(i: Int): Int = i
      |}
      |
      |(O.f)(1)
      |""",
    """
      |(O.f)(1)
      |  target:   f
      |  thisExpr: O
      |  (i = 1) (explicit, after `(O.f)`)
      |O.f
      |  no call
      |O
      |  no call
      |"""
  )

  def testApplyMethodOfObject(): Unit = doTest(
    """
      |object O {
      |  def apply[T](t: T): T = t
      |}
      |
      |O(1)
      |""",
    """
      |O(1)
      |  props:    apply
      |  target:   apply
      |  thisExpr: O
      |  [T = ?Int] (inferred, after `O`)
      |  (t = 1) (explicit, after `O`)
      |O
      |  no call
      |"""
  )

  def testCallOnTheResultOfACall(): Unit = doTest(
    """
      |object O {
      |  def f[T](t: T): O.type = O
      |  def g[S](s: S): Unit = ()
      |}
      |
      |O.f(1).g("s")
      |""",
    """
      |O.f(1).g("s")
      |  target:   g
      |  thisExpr: O.f(1)
      |  [S = ?String] (inferred, after `O.f(1).g`)
      |  (s = "s") (explicit, after `O.f(1).g`)
      |O.f(1).g
      |  no call
      |O.f(1)
      |  target:   f
      |  thisExpr: O
      |  [T = ?Int] (inferred, after `O.f`)
      |  (t = 1) (explicit, after `O.f`)
      |O.f
      |  no call
      |O
      |  no call
      |"""
  )

  def testUpdateCall(): Unit = doTest(
    """
      |class A {
      |  def update(i: Int, s: String): Unit = ()
      |}
      |
      |val a = new A
      |a(0) = "s"
      |""",
    """
      |a(0) = "s"
      |  props:    update
      |  target:   update
      |  thisExpr: a
      |  (i = 0, s = "s") (explicit, after `a`)
      |a(0)
      |  no call
      |a
      |  no call
      |"""
  )

  def testUpdateCallWithImplicits(): Unit = doTest(
    """
      |class A {
      |  def update(i: Int, s: String)(implicit x: Int): Unit = ()
      |}
      |
      |implicit val int: Int = 1
      |
      |val a = new A
      |a(0) = "s"
      |""",
    """
      |a(0) = "s"
      |  props:    update
      |  target:   update
      |  thisExpr: a
      |  (i = 0, s = "s") (explicit, after `a`)
      |  (using int) (inferred, after `a(0)`)
      |a(0)
      |  no call
      |a
      |  no call
      |"""
  )

  def testJavaMethod(): Unit = doTest(
    """
      |"abc".substring(1)
      |""",
    """
      |"abc".substring(1)
      |  target:   substring
      |  thisExpr: "abc"
      |  (beginIndex = 1) (explicit, after `"abc".substring`)
      |"abc".substring
      |  no call
      |"""
  )

  def testJavaMethodWithAnEmptyArgumentList(): Unit = doTest(
    """
      |"abc".length()
      |""",
    """
      |"abc".length()
      |  target:   length
      |  thisExpr: "abc"
      |  () (explicit, after `"abc".length`)
      |"abc".length
      |  no call
      |"""
  )

  /**
   * A Java callee with an empty parameter list is applied by a mere reference to it, which is auto
   * application, so the call applies every clause it has and is not partially applied.
   */
  def testJavaMethodWithoutAnArgumentList(): Unit = doTest(
    """
      |"abc".length
      |""",
    """
      |"abc".length
      |  target:   length
      |  thisExpr: "abc"
      |  () (auto applied, after `"abc".length`)
      |"""
  )

  /**
   * Auto application applies an empty clause only, a clause with parameters is eta expanded even
   * where the callee is a Java method.
   */
  def testJavaMethodWithParametersIsEtaExpanded(): Unit = doTest(
    """
      |val f: Int => String = "abc".substring
      |""",
    """
      |"abc".substring
      |  props:    partially applied
      |  target:   substring
      |  thisExpr: "abc"
      |  (beginIndex: Int) (eta expanded, after `"abc".substring`)
      |"""
  )

  /**
   * An expected type that asks for the function which takes the empty clause of a Java callee eta
   * expands that clause rather than applying it, so the reference is no complete call after all.
   */
  def testAutoAppliedClauseIsEtaExpandedWhereAFunctionIsExpected(): Unit = doTest(
    """
      |val f: () => Int = "abc".length
      |""",
    """
      |"abc".length
      |  props:    partially applied
      |  target:   length
      |  thisExpr: "abc"
      |  () (eta expanded, after `"abc".length`)
      |"""
  )

  //-----------------------------------------------------------------------------------------------
  // constructor calls
  //-----------------------------------------------------------------------------------------------

  def testConstructorInvocation(): Unit = doTest(
    """
      |class A[T](t: T)
      |
      |new A(1)
      |""",
    """
      |A(1)
      |  props:    constructor invocation
      |  target:   A
      |  [T = ?Int] (inferred, after `A`)
      |  (t = 1) (explicit, after `A`)
      |"""
  )

  def testConstructorInvocationWithExplicitTypeArgument(): Unit = doTest(
    """
      |class A[T](t: T)
      |
      |new A[Int](1)
      |""",
    """
      |A[Int](1)
      |  props:    constructor invocation
      |  target:   A
      |  [T = Int] (explicit, after `A[Int]`)
      |  (t = 1) (explicit, after `A[Int]`)
      |"""
  )

  def testConstructorInvocationWithMultipleClauses(): Unit = doTest(
    """
      |class A[T](t: T)(s: String)
      |
      |new A(1)("s")
      |""",
    """
      |A(1)("s")
      |  props:    constructor invocation
      |  target:   A
      |  [T = ?Int] (inferred, after `A`)
      |  (t = 1) (explicit, after `A`)
      |  (s = "s") (explicit, after `(1)`)
      |"""
  )

  def testConstructorInvocationWithImplicitClause(): Unit = doTest(
    """
      |class A[T](t: T)(implicit s: String)
      |implicit val string: String = "s"
      |
      |new A(1)
      |""",
    """
      |A(1)
      |  props:    constructor invocation
      |  target:   A
      |  [T = ?Int] (inferred, after `A`)
      |  (t = 1) (explicit, after `A`)
      |  (using string) (inferred, after `(1)`)
      |"""
  )

  def testConstructorInvocationWithTemplateBody(): Unit = doTest(
    """
      |class A[T](t: T)
      |
      |new A(1) {}
      |""",
    """
      |A(1)
      |  props:    constructor invocation
      |  target:   A
      |  [T = ?Int] (inferred, after `A`)
      |  (t = 1) (explicit, after `A`)
      |"""
  )

  /**
   * A constructor has to be applied to every clause it takes, so an empty one the source leaves out
   * is applied by the compiler rather than eta expanded, `new A` constructing as `new A()` does.
   */
  def testConstructorInvocationWithoutArgumentList(): Unit = doTest(
    """
      |class A
      |
      |new A
      |""",
    """
      |A
      |  props:    constructor invocation
      |  target:   A
      |  () (auto applied, after `A`)
      |"""
  )

  def testConstructorInvocationOfAnEmptyClauseWithoutArgumentList(): Unit = doTest(
    """
      |class A()
      |
      |new A
      |""",
    """
      |A
      |  props:    constructor invocation
      |  target:   A
      |  () (auto applied, after `A`)
      |"""
  )

  def testConstructorInvocationOfAn2EmptyClauseWithoutArgumentList(): Unit = doTest(
    """
      |class A()()
      |
      |new A
      |""",
    """
      |A
      |  props:    constructor invocation
      |  target:   A
      |  () (auto applied, after `A`)
      |  () (auto applied, after `A`)
      |"""
  )

  def testConstructorInvocationWithAnEmptyArgumentList(): Unit = doTest(
    """
      |class A()
      |
      |new A()
      |""",
    """
      |A()
      |  props:    constructor invocation
      |  target:   A
      |  () (explicit, after `A`)
      |"""
  )

  /** An empty clause of a secondary constructor is applied without an argument list as well. */
  def testSecondaryConstructorInvocationWithoutArgumentList(): Unit = doTest(
    """
      |class A(i: Int) {
      |  def this() = this(1)
      |}
      |
      |new A
      |""",
    """
      |A
      |  props:    constructor invocation
      |  target:   this
      |  () (auto applied, after `A`)
      |"""
  )

  /** A class the source leaves an argument list out for is a parent as much as it is a `new`. */
  def testParentConstructorInvocationWithoutArgumentList(): Unit = doTestOfLastConstructorInvocation(
    """
      |class A()
      |
      |class B extends A
      |""",
    """
      |A
      |  props:    constructor invocation
      |  target:   A
      |  () (auto applied, after `A`)
      |"""
  )

  /** A Java constructor declares its empty parameter list, which `new` applies just the same. */
  def testJavaConstructorInvocationWithoutArgumentList(): Unit = doTest(
    """
      |new java.lang.StringBuilder
      |""",
    """
      |java.lang.StringBuilder
      |  props:    constructor invocation
      |  target:   StringBuilder
      |  () (auto applied, after `java.lang.StringBuilder`)
      |"""
  )

  def testPlaceholderInAConstructorInvocation(): Unit = doTest(
    """
      |class A(i: Int)
      |
      |val g = new A(_)
      |""",
    """
      |A(_)
      |  props:    constructor invocation, partially applied
      |  target:   A
      |  (i = _) (explicit, after `A`)
      |_
      |  no call
      |"""
  )

  def testPlaceholderOfOneOfSeveralConstructorArguments(): Unit = doTest(
    """
      |class A(i: Int, j: Int)
      |
      |val g = new A(1, _)
      |""",
    """
      |A(1, _)
      |  props:    constructor invocation, partially applied
      |  target:   A
      |  (i = 1, j = _) (explicit, after `A`)
      |_
      |  no call
      |"""
  )

  /** The same call without `new`, which goes to the `apply` of the companion rather than to `A` itself. */
  def testPlaceholderInAnApplyOfACaseClass(): Unit = doTest(
    """
      |case class A(i: Int)
      |
      |val g = A(_)
      |""",
    """
      |A(_)
      |  props:    apply, partially applied
      |  target:   apply
      |  thisExpr: A
      |  (i = _) (explicit, after `A`)
      |A
      |  no call
      |_
      |  no call
      |"""
  )

  def testConstructorInvocationWithAPlaceholderArgumentIsNotPartiallyApplied(): Unit = doTest(
    """
      |class A(g: Int => Int)
      |def h(i: Int): Int = i
      |
      |val a = new A(h(_))
      |""",
    """
      |A(h(_))
      |  props:    constructor invocation
      |  target:   A
      |  (g = h(_)) (explicit, after `A`)
      |h(_)
      |  props:    partially applied
      |  target:   h
      |  (i = _) (explicit, after `h`)
      |h
      |  no call
      |_
      |  no call
      |"""
  )

  def testParentConstructorInvocation(): Unit = doTestOfLastConstructorInvocation(
    """
      |class A[T](t: T)
      |
      |class B extends A(1)
      |""",
    """
      |A(1)
      |  props:    constructor invocation
      |  target:   A
      |  [T = ?Int] (inferred, after `A`)
      |  (t = 1) (explicit, after `A`)
      |"""
  )

  def testParentConstructorInvocationOfAnObject(): Unit = doTestOfLastConstructorInvocation(
    """
      |class A[T](t: T)
      |
      |object O extends A(1)
      |""",
    """
      |A(1)
      |  props:    constructor invocation
      |  target:   A
      |  [T = ?Int] (inferred, after `A`)
      |  (t = 1) (explicit, after `A`)
      |"""
  )

  def testSelfInvocation(): Unit = doTestOfLastConstructorInvocation(
    """
      |class A(i: Int, s: String) {
      |  def this() = this(1, "s")
      |}
      |""",
    """
      |this(1, "s")
      |  props:    constructor invocation
      |  target:   A
      |  (i = 1, s = "s") (explicit, after `this`)
      |"""
  )

  def testSelfInvocationWithImplicitClause(): Unit = doTestOfLastConstructorInvocation(
    """
      |class A(i: Int)(implicit s: String) {
      |  def this() = this(1)
      |}
      |implicit val string: String = "s"
      |""",
    """
      |this(1)
      |  props:    constructor invocation
      |  target:   A
      |  (i = 1) (explicit, after `this`)
      |  (using string) (inferred, after `(1)`)
      |"""
  )

  def testSelfInvocationWithImplicitClauseSatisfiedByNothing(): Unit = doTestOfLastConstructorInvocation(
    """
      |class A(i: Int)(implicit s: String) {
      |  def this() = this(1)
      |}
      |""",
    """
      |this(1)
      |  props:    constructor invocation
      |  target:   A
      |  (i = 1) (explicit, after `this`)
      |  (using <not found>) (inferred, after `(1)`)
      |"""
  )

  def testSelfInvocationOfGenericClassWithImplicit(): Unit = doTestOfLastConstructorInvocation(
    """
      |class A[T](t: T)(implicit s: String) {
      |  def this(t: T, u: T) = this(t)
      |}
      |implicit val string: String = "s"
      |""",
    """
      |this(t)
      |  props:    constructor invocation
      |  target:   A
      |  (t = t) (explicit, after `this`)
      |  (using string) (inferred, after `(t)`)
      |"""
  )

  def testSelfInvocationOfSecondaryConstructor(): Unit = doTestOfLastConstructorInvocation(
    """
      |class A(i: Int)(using s: String) {
      |  def this(s: String) = this(1)
      |  def this() = this("s")
      |}
      |implicit val string: String = "s"
      |""",
    """
      |this("s")
      |  props:    constructor invocation
      |  target:   this
      |  (s = "s") (explicit, after `this`)
      |"""
  )


  //-----------------------------------------------------------------------------------------------

  /**
   * Renders every expression of the last statement of the file, outermost first, with the call it is,
   * or `no call` if it is none, and compares that against `expected`.
   */
  protected def doTest(code: String, expected: String): Unit = {
    val file       = configureFromFileText(code.stripMargin)
    val expression = lastExpressionIn(file)

    assertEquals(expected.stripMargin.trim, render(expression))
  }


  /**
   * Renders the last constructor invocation of the file, for the forms that are not part of an
   * expression: the parents of a template, the constructor invocation of a given definition and the
   * self invocation of a secondary constructor.
   */
  protected def doTestOfLastConstructorInvocation(code: String, expected: String): Unit = {
    val file = configureFromFileText(code.stripMargin)

    val invocation = file.depthFirst().filterByType[ConstructorInvocationLike].toSeq.lastOption
      .getOrElse(throw new AssertionError("no constructor invocation in the file"))

    implicit val presentationContext: TypePresentationContext = TypePresentationContext(invocation)
    implicit val context: Context = Context(invocation)

    val details = invocation.invocationDetails.fold(Seq("no call"))(render)

    assertEquals(expected.stripMargin.trim, (textOf(invocation) +: details.map("  " + _)).mkString("\n"))
  }

  /**
   * The expression of the last statement of the file, whose calls are under test. The body of a
   * method counts as one, so that a call can be tested where the type parameters of an enclosing
   * declaration are in scope, as a recursive call is.
   */
  private def lastExpressionIn(file: PsiElement): ScExpression = {
    val lastStatement = file.getChildren.reverseIterator.collectFirst {
      case expression: ScExpression                => expression
      case definition: ScValueOrVariableDefinition => definition.expr.get
      case function: ScFunctionDefinition          => function.body.get
    }

    lastStatement.getOrElse(throw new AssertionError("the last statement of the file is not an expression"))
  }

  private def render(expression: ScExpression): String = {
    implicit val presentationContext: TypePresentationContext = TypePresentationContext(expression)
    implicit val context: Context = Context(expression)

    val elements = expression.depthFirst().collect {
      case expression: ScExpression                => expression
      case invocation: ConstructorInvocationLike   => invocation
    }

    elements.toSeq.collect {
      case owner: InvocationDetailsOwner =>
        val details = owner.invocationDetails.fold(Seq("no call"))(render)
        (textOf(owner) +: details.map("  " + _)).mkString("\n")
    }.mkString("\n")
  }

  private def render(call: InvocationDetails)(implicit
                                          presentationContext: TypePresentationContext,
                                          context:             Context
  ): Seq[String] = {
    def renderTypeArgument(argument: TypeArgument): String = {
      //a type argument the source does not write out is marked with a question mark
      val inferred = if (argument.isExplicit) "" else "?"

      s"${argument.parameter.name} = $inferred${argument.tpe.presentableText}"
    }

    def renderClause(clause: ArgumentClause, arguments: String): String = {
      val explicitness = clause match {
        case _: AutoAppliedClause        => "auto applied"
        case _: EtaExpandedClause        => "eta expanded"
        case clause if clause.isExplicit => "explicit"
        case _                           => "inferred"
      }

      val autoTupling = clause match {
        case clause: ArgumentClause.Value if clause.isAutoTupling => ", auto tupled"
        case _                                                   => ""
      }

      s"$arguments ($explicitness$autoTupling, after `${textOf(clause.anchor)}`)"
    }

    val clauses = call.argumentClauses.map {
      case clause: TypeClause =>
        renderClause(clause, s"[${clause.arguments.map(renderTypeArgument).mkString(", ")}]")
      case clause: ValueClause =>
        val arguments = clause.arguments.map {
          //a synthetic function like `Int#+` has no names for its parameters
          case (argument, parameter) if parameter.name.isEmpty => textOf(argument)
          case (argument, parameter)                           => s"${parameter.name} = ${textOf(argument)}"
        }
        renderClause(clause, s"(${arguments.mkString(", ")})")
      case clause: ImplicitValueClause =>
        val arguments = clause.arguments.map { argument =>
          if (argument.isImplicitParameterProblem) "<not found>"
          else                                     argument.name
        }
        renderClause(clause, s"(using ${arguments.mkString(", ")})")
      //an eta expanded clause has no arguments, the function the call evaluates to takes them, so its
      //parameters are rendered with the types the call determines for them
      case clause: EtaExpandedClause =>
        val parameters = clause.parameters.map { parameter =>
          if (parameter.name.isEmpty) parameter.paramType.presentableText
          else                        s"${parameter.name}: ${parameter.paramType.presentableText}"
        }
        renderClause(clause, s"(${parameters.mkString(", ")})")
      case clause: AutoAppliedClause =>
        renderClause(clause, "()")
    }

    //the properties of the call, left out when it has none of them, which a call that names its
    //callee and applies it to everything it takes has
    val properties = Seq(
      "apply"                  -> call.isApply,
      "universal apply"        -> call.isUniversalApply,
      "constructor invocation" -> call.isConstructorInvocation,
      "update"                 -> call.isUpdate,
      "assignment call"        -> call.isAssignmentCall,
      "partially applied"      -> call.isPartiallyApplied
    ).collect { case (property, true) => property }

    val header =
      properties.headOption.map(_ => s"props:    ${properties.mkString(", ")}").toSeq ++
        Seq(s"target:   ${call.target.map(_.name).getOrElse("<unresolved>")}") ++
        call.thisExpr.map(textOf).map(thisExpr => s"thisExpr: $thisExpr")

    header ++ clauses
  }

  /** The text of an element on one line, so that the rendering stays one line per expression. */
  private def textOf(element: PsiElement): String =
    element.getText.linesIterator.map(_.trim).mkString(" ")
}

class InvocationDetailsTest_Scala3 extends InvocationDetailsTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  //-----------------------------------------------------------------------------------------------
  // type clauses
  //-----------------------------------------------------------------------------------------------

  def testNamedTypeArgument(): Unit = doTest(
    """
      |import scala.language.experimental.namedTypeArguments
      |
      |def f[T, S](t: T)(s: S): Unit = ()
      |
      |f[T = Int](1)("s")
      |""",
    """
      |f[T = Int](1)("s")
      |  target:   f
      |  [T = Int, S = ?String] (explicit, after `f`)
      |  (t = 1) (explicit, after `f[T = Int]`)
      |  (s = "s") (explicit, after `f[T = Int](1)`)
      |f[T = Int](1)
      |  no call
      |f[T = Int]
      |  no call
      |f
      |  no call
      |"""
  )

  def testInterleavedTypeClauses(): Unit = doTest(
    """
      |def f[T](t: T)[S](s: S): Unit = ()
      |
      |f(1)("s")
      |""",
    """
      |f(1)("s")
      |  target:   f
      |  [T = ?Int] (inferred, after `f`)
      |  (t = 1) (explicit, after `f`)
      |  [S = ?String] (inferred, after `f(1)`)
      |  (s = "s") (explicit, after `f(1)`)
      |f(1)
      |  no call
      |f
      |  no call
      |"""
  )

  def testInterleavedTypeClausesWrittenOut(): Unit = doTest(
    """
      |def f[T](t: T)[S](s: S): Unit = ()
      |
      |f[Int](1)[String]("s")
      |""",
    """
      |f[Int](1)[String]("s")
      |  target:   f
      |  [T = Int] (explicit, after `f`)
      |  (t = 1) (explicit, after `f[Int]`)
      |  [S = String] (explicit, after `f[Int](1)`)
      |  (s = "s") (explicit, after `f[Int](1)[String]`)
      |f[Int](1)[String]
      |  no call
      |f[Int](1)
      |  no call
      |f[Int]
      |  no call
      |f
      |  no call
      |"""
  )

  def testInterleavedTypeClauseWrittenOutAfterInferredOne(): Unit = doTest(
    """
      |def f[T](t: T)[S](s: S): Unit = ()
      |
      |f(1)[String]("s")
      |""",
    """
      |f(1)[String]("s")
      |  target:   f
      |  [T = ?Int] (inferred, after `f`)
      |  (t = 1) (explicit, after `f`)
      |  [S = String] (explicit, after `f(1)`)
      |  (s = "s") (explicit, after `f(1)[String]`)
      |f(1)[String]
      |  no call
      |f(1)
      |  no call
      |f
      |  no call
      |"""
  )

  //-----------------------------------------------------------------------------------------------
  // implicit clauses
  //-----------------------------------------------------------------------------------------------

  def testTrailingUsingClause(): Unit = doTest(
    """
      |def f(i: Int)(using s: String): Unit = ()
      |given String = "s"
      |
      |f(1)
      |""",
    """
      |f(1)
      |  target:   f
      |  (i = 1) (explicit, after `f`)
      |  (using given_String) (inferred, after `f(1)`)
      |f
      |  no call
      |"""
  )

  /** The arguments of an `update` are spread over an assignment, and its using clause follows them. */
  def testUpdateCallWithUsingClause(): Unit = doTest(
    """
      |class A {
      |  def update(i: Int, s: String)(using Int): Unit = ()
      |}
      |
      |given Int = 1
      |
      |val a = new A
      |a(0) = "s"
      |""",
    """
      |a(0) = "s"
      |  props:    update
      |  target:   update
      |  thisExpr: a
      |  (i = 0, s = "s") (explicit, after `a`)
      |  (using given_Int) (inferred, after `a(0)`)
      |a(0)
      |  no call
      |a
      |  no call
      |"""
  )

  def testUsingClauseOnly(): Unit = doTest(
    """
      |def f(using s: String): Unit = ()
      |given String = "s"
      |
      |f
      |""",
    """
      |f
      |  target:   f
      |  (using given_String) (inferred, after `f`)
      |"""
  )

  def testLeadingUsingClause(): Unit = doTest(
    """
      |def f(using s: String)(i: Int): Unit = ()
      |given String = "s"
      |
      |f(1)
      |""",
    """
      |f(1)
      |  target:   f
      |  (using given_String) (inferred, after `f`)
      |  (i = 1) (explicit, after `f`)
      |f
      |  no call
      |"""
  )

  def testMultipleUsingClauses(): Unit = doTest(
    """
      |def f(using s: String)(using i: Int): Unit = ()
      |given String = "s"
      |given Int = 1
      |
      |f
      |""",
    """
      |f
      |  target:   f
      |  (using given_String) (inferred, after `f`)
      |  (using given_Int) (inferred, after `f`)
      |"""
  )

  def testExplicitUsingArguments(): Unit = doTest(
    """
      |def f(i: Int)(using s: String): Unit = ()
      |
      |f(1)(using "s")
      |""",
    """
      |f(1)(using "s")
      |  target:   f
      |  (i = 1) (explicit, after `f`)
      |  (s = "s") (explicit, after `f(1)`)
      |f(1)
      |  no call
      |f
      |  no call
      |"""
  )

  def testNotFoundUsingArgument(): Unit = doTest(
    """
      |def f(i: Int)(using s: String): Unit = ()
      |
      |f(1)
      |""",
    """
      |f(1)
      |  target:   f
      |  (i = 1) (explicit, after `f`)
      |  (using <not found>) (inferred, after `f(1)`)
      |f
      |  no call
      |"""
  )

  def testTypeArgumentFromUsingArgument(): Unit = doTest(
    """
      |def get[A](using a: A): A = ???
      |given Int = 3
      |
      |val x: Int = get
      |""",
    """
      |get
      |  target:   get
      |  [A = ?Int] (inferred, after `get`)
      |  (using given_Int) (inferred, after `get`)
      |"""
  )

  def testTypeArgumentFromUsingArgumentOfTrailingClause(): Unit = doTest(
    """
      |trait Show[A]
      |given Show[Int] = ???
      |
      |def show[A](i: Int)(using s: Show[A]): Unit = ()
      |
      |show(1)
      |""",
    """
      |show(1)
      |  target:   show
      |  [A = ?Int] (inferred, after `show`)
      |  (i = 1) (explicit, after `show`)
      |  (using given_Show_Int) (inferred, after `show(1)`)
      |show
      |  no call
      |"""
  )

  def testTypeClauseAfterTermClause(): Unit = doTest(
    """
      |def get[A](x: A)[B <: A](using y: B): A = ???
      |given Int = 3
      |
      |val x: Int = get(1)
      |""",
    """
      |get(1)
      |  target:   get
      |  [A = ?Int] (inferred, after `get`)
      |  (x = 1) (explicit, after `get`)
      |  [B = ?Int] (inferred, after `get(1)`)
      |  (using given_Int) (inferred, after `get(1)`)
      |get
      |  no call
      |"""
  )

  def testTypeClausesAroundUsingClauses(): Unit = doTest(
    """
      |def get[A](using x: A)[B <: A](using y: B): A = ???
      |given Int = 3
      |
      |val x: Int = get[Int][Int]
      |""",
    """
      |get[Int][Int]
      |  target:   get
      |  [A = Int] (explicit, after `get`)
      |  (using given_Int) (inferred, after `get[Int]`)
      |  [B = Int] (explicit, after `get[Int]`)
      |  (using given_Int) (inferred, after `get[Int][Int]`)
      |get[Int]
      |  no call
      |get
      |  no call
      |"""
  )

  def testUnconstrainedTypeClauseAfterUsingClause(): Unit = doTest(
    """
      |def get[A](using A)[B]: A = ???
      |given Int = 3
      |
      |val x: Int = get[Int]
      |""",
    """
      |get[Int]
      |  target:   get
      |  [A = Int] (explicit, after `get`)
      |  (using given_Int) (inferred, after `get[Int]`)
      |  [B = ?Nothing] (inferred, after `get[Int]`)
      |get
      |  no call
      |"""
  )

  def testUsingClausesBeforeApplyOnResult(): Unit = doTest(
    """
      |class X {
      |  def apply(s: String): Int = 3
      |}
      |def get[A](using x: A)[B <: A](using y: B): X = ???
      |given Int = 3
      |
      |get[Int]("hello")
      |""",
    """
      |get[Int]("hello")
      |  props:    apply
      |  target:   apply
      |  thisExpr: get[Int]
      |  (s = "hello") (explicit, after `get[Int]`)
      |get[Int]
      |  target:   get
      |  [A = Int] (explicit, after `get`)
      |  (using given_Int) (inferred, after `get[Int]`)
      |  [B = ?Int] (inferred, after `get[Int]`)
      |  (using given_Int) (inferred, after `get[Int]`)
      |get
      |  no call
      |"""
  )

  //-----------------------------------------------------------------------------------------------
  // partial application
  //-----------------------------------------------------------------------------------------------

  def testEtaExpansionAppliesItsUsingClause(): Unit = doTest(
    """
      |def f(using i: Int)(j: Int): Int = 3
      |given Int = 1
      |
      |val g = f
      |""",
    """
      |f
      |  props:    partially applied
      |  target:   f
      |  (using given_Int) (inferred, after `f`)
      |  (j: Int) (eta expanded, after `f`)
      |"""
  )

  /**
   * A using clause is eta expanded rather than filled in by the implicit search when the expected
   * type asks for a context function, so the same `f` reports the same clause either way.
   */
  def testEtaExpansionOfAUsingClause(): Unit = doTest(
    """
      |def f(i: Int)(using s: Int): Int = 3
      |given Int = 1
      |
      |val g: Int => Int ?=> Int = f
      |""",
    """
      |f
      |  props:    partially applied
      |  target:   f
      |  (i: Int) (eta expanded, after `f`)
      |  (s: Int) (eta expanded, after `f`)
      |"""
  )

  /** The same `f`, with an expected type that asks for the using clause to be applied instead. */
  def testUsingClauseOfAnEtaExpansionIsStillFilledIn(): Unit = doTest(
    """
      |def f(i: Int)(using s: Int): Int = 3
      |given Int = 1
      |
      |val g: Int => Int = f
      |""",
    """
      |f
      |  props:    partially applied
      |  target:   f
      |  (i: Int) (eta expanded, after `f`)
      |  (using given_Int) (inferred, after `f`)
      |"""
  )

  //-----------------------------------------------------------------------------------------------
  // a clause the callee does not have is applied to the result of the call, which is another call
  //-----------------------------------------------------------------------------------------------

  def testApplyOfAValue(): Unit = doTest(
    """
      |val x = Seq(1)
      |
      |x(3)
      |""",
    """
      |x(3)
      |  props:    apply
      |  target:   apply
      |  thisExpr: x
      |  (i = 3) (explicit, after `x`)
      |x
      |  no call
      |"""
  )

  //-----------------------------------------------------------------------------------------------
  // the syntactic forms of a call
  //-----------------------------------------------------------------------------------------------

  def testUniversalApply(): Unit = doTest(
    """
      |class A[T](t: T)
      |
      |A(1)
      |""",
    """
      |A(1)
      |  props:    universal apply, constructor invocation
      |  target:   A
      |  [T = ?Int] (inferred, after `A`)
      |  (t = 1) (explicit, after `A`)
      |A
      |  no call
      |"""
  )

  def testExtensionMethod(): Unit = doTest(
    """
      |extension (i: Int) def f[T](t: T): T = t
      |
      |1.f("s")
      |""",
    """
      |1.f("s")
      |  target:   f
      |  thisExpr: 1
      |  [T = ?String] (inferred, after `1.f`)
      |  (t = "s") (explicit, after `1.f`)
      |1.f
      |  no call
      |"""
  )

  //-----------------------------------------------------------------------------------------------
  // constructor calls
  //-----------------------------------------------------------------------------------------------

  def testConstructorInvocationWithUsingClause(): Unit = doTest(
    """
      |class A[T](t: T)(using s: String)
      |given String = "s"
      |
      |new A(1)
      |""",
    """
      |A(1)
      |  props:    constructor invocation
      |  target:   A
      |  [T = ?Int] (inferred, after `A`)
      |  (t = 1) (explicit, after `A`)
      |  (using given_String) (inferred, after `(1)`)
      |"""
  )

  def testConstructorInvocationOfAGiven(): Unit = doTestOfLastConstructorInvocation(
    """
      |class A[T](t: T)
      |
      |given A[Int] = new A(1)
      |""",
    """
      |A(1)
      |  props:    constructor invocation
      |  target:   A
      |  [T = ?Int] (inferred, after `A`)
      |  (t = 1) (explicit, after `A`)
      |"""
  )

  def testConstructorInvocationOfAGivenWithTemplateBody(): Unit = doTestOfLastConstructorInvocation(
    """
      |class A[T](t: T)
      |
      |given A(3) {}
      |""",
    """
      |A(3)
      |  props:    constructor invocation
      |  target:   A
      |  [T = ?Int] (inferred, after `A`)
      |  (t = 3) (explicit, after `A`)
      |"""
  )

  def testSelfInvocationWithUsingClause(): Unit = doTestOfLastConstructorInvocation(
    """
      |class A(i: Int)(using s: String) {
      |  def this() = this(1)
      |}
      |given String = "s"
      |""",
    """
      |this(1)
      |  props:    constructor invocation
      |  target:   A
      |  (i = 1) (explicit, after `this`)
      |  (using given_String) (inferred, after `(1)`)
      |"""
  )

  def testSelfInvocationWithUsingClauseSatisfiedByNothing(): Unit = doTestOfLastConstructorInvocation(
    """
      |class A(i: Int)(using s: String) {
      |  def this() = this(1)
      |}
      |""",
    """
      |this(1)
      |  props:    constructor invocation
      |  target:   A
      |  (i = 1) (explicit, after `this`)
      |  (using <not found>) (inferred, after `(1)`)
      |"""
  )

  def testSelfInvocationOfGenericClassWithUsing(): Unit = doTestOfLastConstructorInvocation(
    """
      |class A[T](t: T)(using s: String) {
      |  def this(t: T, u: T) = this(t)
      |}
      |given String = "s"
      |""",
    """
      |this(t)
      |  props:    constructor invocation
      |  target:   A
      |  (t = t) (explicit, after `this`)
      |  (using given_String) (inferred, after `(t)`)
      |"""
  )
}

class InvocationDetailsTest_Scala2 extends InvocationDetailsTestBase {
  def testAutoTuplingOfAnEmptyArgumentList(): Unit = doTest(
    """
      |def f(u: Unit): Unit = ()
      |
      |f()
      |""",
    """
      |f()
      |  target:   f
      |  () (explicit, auto tupled, after `f`)
      |f
      |  no call
      |"""
  )

  def testExplicitUnitIsNotAutoTupling(): Unit = doTest(
    """
      |def f(u: Unit): Unit = ()
      |
      |f(())
      |""",
    """
      |f(())
      |  target:   f
      |  (u = ()) (explicit, after `f`)
      |f
      |  no call
      |"""
  )

  def testDefaultArgumentIsNotAutoTupling(): Unit = doTest(
    """
      |def f(u: Unit = ()): Unit = ()
      |
      |f()
      |""",
    """
      |f()
      |  target:   f
      |  (u = ()) (explicit, after `f`)
      |f
      |  no call
      |"""
  )

  def testAutoTuplingOfAnEmptyConstructorArgumentList(): Unit = doTest(
    """
      |class C(u: Unit)
      |
      |new C()
      |""",
    """
      |C()
      |  props:    constructor invocation
      |  target:   C
      |  () (explicit, auto tupled, after `C`)
      |"""
  )

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala2


  //-----------------------------------------------------------------------------------------------
  // a clause the callee does not have is applied to the result of the call, which is another call
  //-----------------------------------------------------------------------------------------------

  def testApplyOfAValue(): Unit = doTest(
    """
      |val x = Seq(1)
      |
      |x(3)
      |""",
    """
      |x(3)
      |  props:    apply
      |  target:   apply
      |  thisExpr: x
      |  (idx = 3) (explicit, after `x`)
      |x
      |  no call
      |"""
  )
}
