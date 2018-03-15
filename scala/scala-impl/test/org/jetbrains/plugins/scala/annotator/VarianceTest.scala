package org.jetbrains.plugins.scala
package annotator

/**
 * @author Svyatoslav ILINSKIY
 * @since  6/27/2014.
 */
class VarianceTest extends VarianceTestBase {
  def testVarianceParameter() {
    assertMatches(messages("trait Agent[+S] { def nextAction(state: S) }")) {
      case Error("state", ContravariantPosition()) :: Nil =>
    }
  }

  def testVarianceReturnType() {
    assertMatches(messages("trait C[-S] {def f: S}")) {
      case Error("f", CovariantPosition()) :: Nil =>
    }
  }

  def testVarianceTypeAlias() {
    assertMatches(messages("trait D[-S] { type al = S; def f: al }")) {
      case Error("f", CovariantPosition()) :: Nil =>
    }
  }

  def testVarianceParameterizedParameter() {
    assertMatches(messages("trait E[-P[+_], +R] {def a(x: P[R])}")) {
      case Error("x", ContravariantPosition()) :: Nil =>
    }
  }

  def testVarianceParameterizedReturnType() {
    assertMatches(messages("abstract class F[-P[+_], +R](in: P[R]) {def a = in}")) {
      case Error("a", CovariantPosition()) :: Nil =>
    }
  }

  def testVariancePrivateThis() {
    assertMatches(messages("private[this] abstract class G[-P[+_], +R](in: P[R]) {def a = in}")) {
      case Error("a", CovariantPosition()) :: Nil =>
    }
  }

  def testFunctionInsideFunction() {
    assertMatches(messages("trait H[+S] { def outer = {def inner(s: S) = s }}")) {
      case Nil =>
    }
  }

  def testVariableCovariantParam() {
    assertMatches(messages("trait I[+S] {var k: S}")) {
      case Error("k", ContravariantPosition()) :: Nil =>
    }
  }
  def testVariableContravariantParam() {
    assertMatches(messages("trait J[-S] {var k: S}")) {
      case Error("k", CovariantPosition()) :: Nil =>
    }
  }

  def testVariableInsideFunction() {
    assertMatches(messages("trait K[-S] {def f(s: S) {var k: S = s}}")) {
      case Nil =>
    }
  }

  def testValueContravariantParam() {
    assertMatches(messages("trait L[-S] {val k: S}")) {
      case Error("k", CovariantPosition()) :: Nil =>
    }
  }

  def testValueCovariantParam() {
    assertMatches(messages("trait M[+S] {val k: S}")) {
      case Nil =>
    }
  }

  def testAbstractPrivateMethod() { //test SCL-7176
    assertMatches(messages("private def x")) {
      case Error("x", AbstractModifier()) :: Nil =>
    }
  }

  def testBoundsConformance() {
    assertMatches(messages("trait T[Q, R <: Q, C >: Q <: R]")) {
      case Error("C >: Q <: R", NotConformsUpper()) :: Nil =>
    }
  }

  def testTypeBoundsNoError() {
    assertMatches(messages("trait U[M[+X] <: W[X], W[+_]")) {
      case Nil =>
    }
  }

  def testTypeBoundNoErrorParameterized() {
    assertMatches(messages("trait V[M[X <: Bound[X]], Bound[_]]")) {
      case Nil =>
    }
  }

  def testComplexTypeBoundsNoError() {
    assertMatches(messages("abstract class B[-T, S[Z <: T] >: T, P >: T]")) {
      case Nil =>
    }
  }

  def testSimpleTypeAlias() {
    assertMatches(messages("trait T[-T] { type S <: T }")) {
      case Error("S", CovariantPosition()) :: Nil =>
    }
  }

  def testBoundDefinedInsideOwner() {
    assertMatches(messages("trait B[Z[-P, A <: P] <: P]")) {
      case Error("Z", CovariantPosition()) :: Nil =>
    }
  }

  def testBoundDefinedInsideTrait() {
    assertMatches(messages("trait X[-P, A <: P]")) {
      case Error("A", CovariantPosition()) :: Nil =>
    }
  }

  def testSCL8803() {
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

  def testSCL8863() = {
    assertMatches(messages(
      """
        |class Test[+T]{
        |  var arr: Array[T@uncheckedVariance] = null
        |}
      """.stripMargin)) {
      case Nil =>
    }
  }

  def testUV() = {
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
}
