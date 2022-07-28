package org.jetbrains.plugins.scala
package annotator

class VarianceTest extends VarianceTestBase {
  def testVarianceParameter(): Unit = {
    assertMatches(messages("trait Agent[+S] { def nextAction(state: S) }")) {
      case Error("state", ContravariantPosition()) :: Nil =>
    }
  }

  def testVarianceReturnType(): Unit = {
    assertMatches(messages("trait C[-S] {def f: S}")) {
      case Error("f", CovariantPosition()) :: Nil =>
    }
  }

  def testVarianceTypeAlias(): Unit = {
    assertMatches(messages("trait D[-S] { type al = S; def f: al }")) {
      case Error("f", CovariantPosition()) :: Nil =>
    }
  }

  def testVarianceParameterizedParameter(): Unit = {
    assertMatches(messages("trait E[-P[+_], +R] {def a(x: P[R])}")) {
      case Error("x", ContravariantPosition()) :: Nil =>
    }
  }

  def testVarianceParameterizedReturnType(): Unit = {
    assertMatches(messages("abstract class F[-P[+_], +R](in: P[R]) {def a = in}")) {
      case Error("a", CovariantPosition()) :: Nil =>
    }
  }

  def testVariancePrivateThis(): Unit = {
    assertMatches(messages("private[this] abstract class G[-P[+_], +R](in: P[R]) {def a = in}")) {
      case Error("a", CovariantPosition()) :: Nil =>
    }
  }

  def testFunctionInsideFunction(): Unit = {
    assertMatches(messages("trait H[+S] { def outer = {def inner(s: S) = s }}")) {
      case Nil =>
    }
  }

  def testVariableCovariantParam(): Unit = {
    assertMatches(messages("trait I[+S] {var k: S}")) {
      case Error("k", ContravariantPosition()) :: Nil =>
    }
  }
  def testVariableContravariantParam(): Unit = {
    assertMatches(messages("trait J[-S] {var k: S}")) {
      case Error("k", CovariantPosition()) :: Nil =>
    }
  }

  def testVariableInsideFunction(): Unit = {
    assertMatches(messages("trait K[-S] {def f(s: S) {var k: S = s}}")) {
      case Nil =>
    }
  }

  def testValueContravariantParam(): Unit = {
    assertMatches(messages("trait L[-S] {val k: S}")) {
      case Error("k", CovariantPosition()) :: Nil =>
    }
  }

  def testValueCovariantParam(): Unit = {
    assertMatches(messages("trait M[+S] {val k: S}")) {
      case Nil =>
    }
  }

  def testAbstractPrivateMethod(): Unit = { //test SCL-7176
    assertMatches(messages("private def x")) {
      case Error("x", AbstractModifier()) :: Nil =>
    }
  }

  def testBoundsConformance(): Unit = {
    assertMatches(messages("trait T[Q, R <: Q, C >: Q <: R]")) {
      case Error("C >: Q <: R", NotConformsUpper()) :: Nil =>
    }
  }

  def testTypeBoundsNoError(): Unit = {
    assertMatches(messages("trait U[M[+X] <: W[X], W[+_]")) {
      case Nil =>
    }
  }

  def testTypeBoundNoErrorParameterized(): Unit = {
    assertMatches(messages("trait V[M[X <: Bound[X]], Bound[_]]")) {
      case Nil =>
    }
  }

  def testComplexTypeBoundsNoError(): Unit = {
    assertMatches(messages("abstract class B[-T, S[Z <: T] >: T, P >: T]")) {
      case Nil =>
    }
  }

  def testSimpleTypeAlias(): Unit = {
    assertMatches(messages("trait T[-T] { type S <: T }")) {
      case Error("S", CovariantPosition()) :: Nil =>
    }
  }

  def testBoundDefinedInsideOwner(): Unit = {
    assertMatches(messages("trait B[Z[-P, A <: P] <: P]")) {
      case Error("Z", CovariantPosition()) :: Nil =>
    }
  }

  def testBoundDefinedInsideTrait(): Unit = {
    assertMatches(messages("trait X[-P, A <: P]")) {
      case Error("A", CovariantPosition()) :: Nil =>
    }
  }

  def testSCL8803(): Unit = {
    assertMatches(messages(
      """object Main extends App {
        |
        |  class Sum[+T](dummy: T, val sel: Int) {
        |    def this(d: T, value: List[Int]) = this(d, value.sum)
        |  }
        |
        |  println(new Sum(0, List(1, 2)).sel)
        |}""".stripMargin)) {
      case Nil =>
    }
  }

  def testSCL8863(): Unit = {
    assertMatches(messages(
      """
        |class Test[+T]{
        |  var arr: Array[T@uncheckedVariance] = null
        |}
      """.stripMargin)) {
      case Nil =>
    }
  }

  def testUV(): Unit = {
    assertMatches(messages(
      """
        |import scala.annotation.unchecked.{ uncheckedVariance => uV }
        |
        |class Test[+T] {
        |  var arr: Array[T@uV] = null
        |}
      """.stripMargin)) {
      case Nil =>
    }
  }


  def testPublicVarClassParams(): Unit = {
    assertMatches(messages("class AA[+T, -S](var leading: T, var trailing: S)")) {
      case Error("leading", ContravariantPosition()) ::
        Error("trailing", CovariantPosition()) ::
        Nil =>
    }
  }

  def testPrivateThisClassParams(): Unit = {
    assertNothing(messages("class AA[+T, -S](private[this] var leading: T, private[this] var trailing: S)"))
  }
}
