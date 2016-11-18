package org.jetbrains.plugins.scala
package annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeBoundsOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
 * @author Svyatoslav ILINSKIY
 * @since  6/27/2014.
 */
class VarianceTest extends SimpleTestCase {
  final val Header = "class A; class B\n"

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


  def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
    val annotator = new ScalaAnnotator() {}
    val file: ScalaFile = (Header + code).parse
    val mock = new AnnotatorHolderMock(file)

    file.depthFirst.foreach {
      case fun: ScFunction => annotator.annotate(fun, mock)
      case varr: ScVariable => annotator.annotate(varr, mock)
      case v: ScValue => annotator.annotate(v, mock)
      case tbo: ScTypeBoundsOwner => annotator.annotate(tbo, mock)
      case call: ScMethodCall => annotator.annotate(call, mock)
      case td: ScTypeDefinition => annotator.annotate(td, mock)
      case _ =>
    }

    mock.annotations.filter((p: Message) => !p.isInstanceOf[Info])
  }

  val ContravariantPosition = ContainsPattern("occurs in contravariant position")
  val CovariantPosition = ContainsPattern("occurs in covariant position")
  val AbstractModifier = ContainsPattern("Abstract member may not have private modifier")
  val NotConformsUpper = ContainsPattern("doesn't conform to upper bound")
}
