package org.jetbrains.plugins.scala
package annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeBoundsOwner

/**
 * Created by Svyaatoslav ILINSKIY on 6/27/2014.
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

  def testBoundCyclicSimple() {
    assertMatches(messages("trait N[A <: A]")) {
      case Error("N", CyclicReference()) :: Nil =>
    }
  }

  def testBoundCyclicComplex() {
    assertMatches(messages("trait O[B, A >: B with A]")) {
      case Error("O", CyclicReference()) :: Nil =>
    }
  }

  def testBoundCyclicMoreComplex() {
    assertMatches(messages("trait P[A >: B, B >: A]")) {
      case Error("P", CyclicReference()) :: Error("P", CyclicReference()) :: Nil =>
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

  def testCurriedFunctionParameters() {
    val code = """object T {
                 |  def f(x: Int)(y: Int) = x * y
                 |
                 |  def t() : Unit = {
                 |    val cf = f(1)
                 |    // should be f(1) _
                 |    println(cf(5))
                 |  }
                 |}""".stripMargin
    assertMatches(messages(code)) {
      case Error("cf", MissingArguments()) :: Nil =>
    }
  }

  def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
    val annotator = new ScalaAnnotator() {}
    val mock = new AnnotatorHolderMock

    val parse: ScalaFile = (Header + code).parse

    parse.depthFirst.foreach {
      case fun: ScFunction => annotator.annotate(fun, mock)
      case varr: ScVariable => annotator.annotate(varr, mock)
      case v: ScValue => annotator.annotate(v, mock)
      case tbo: ScTypeBoundsOwner => annotator.annotate(tbo, mock)
      case call: ScMethodCall => annotator.annotate(call, mock)
      case _ =>
    }

    mock.annotations.filter((p: Message) => !p.isInstanceOf[Info])
  }

  val ContravariantPosition = containsPattern("occurs in contravariant position")
  val CovariantPosition = containsPattern("occurs in covariant position")
  val AbstractModifier = containsPattern("Abstract member may not have private modifier")
  val CyclicReference = containsPattern("Illegal cyclic reference")
  val NotConformsUpper = containsPattern("does not conform to upper bound")
  val MissingArguments = containsPattern("Missing arguments for method")

  def containsPattern(fragment: String) = new {
    def unapply(s: String) = s.contains(fragment)
  }
}
