package org.jetbrains.plugins.scala.codeInspection.types

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}
import org.jetbrains.plugins.scala.externalLibraries.kindProjector.inspections.KindProjectorSimplifyTypeProjectionInspection
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

class KindProjectorSimplifyTypeProjectionTest extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[KindProjectorSimplifyTypeProjectionInspection]

  override protected val description: String =
    ScalaInspectionBundle.message("kind.projector.simplify.type")

  private def testFix(text: String, res: String): Unit =
    testQuickFix(text, res, description)

  override protected def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val newSettings = defaultProfile.getSettings.copy(
      plugins = defaultProfile.getSettings.plugins :+ "kind-projector"
    )
    defaultProfile.setSettings(newSettings)
  }

  def testEitherInline(): Unit = {
    val code = s"def a: $START({type A[Beta] = Either[Int, Beta]})#A$END"
    checkTextHasError(code)
    val text = "def a: ({type A[Beta] = Either[Int, Beta]})#A"
    val res = "def a: Either[Int, ?]"
    testFix(text, res)
  }

  def testParametersWrongOrder(): Unit = {
    val code = s"def a: $START({type L[A, B] = Either[B, A]})#L$END"
    checkTextHasError(code)
    val text = "def a: ({type L[A, B] = Either[B, A]})#L"
    val res = "def a: Lambda[(A, B) => Either[B, A]] "
    testFix(text, res)
  }

  def testTwoParameters(): Unit = {
    val code = s"def a: $START({type A[-Alpha, +Gamma] = Function2[Alpha, String, Gamma]})#A$END"
    checkTextHasError(code)
    val text = "def a: ({type A[-Alpha, +Gamma] = Function2[Alpha, String, Gamma]})#A"
    val res = "def a: Function2[-?, String, +?]"
    testFix(text, res)
  }

  def testRepeatedParams(): Unit = {
    val code = s"def a: $START({type A[A] = (A, A)})#A$END"
    checkTextHasError(code)
    val text = "def a: ({type A[A] = (A, A)})#A"
    val res = "def a: Lambda[A => (A, A)]"
    testFix(text, res)
  }

  def testCovariant(): Unit = {
    val code = s"def a: $START({type A[+A, B] = Either[A, Option[B]]})#A$END"
    checkTextHasError(code)
    val text = "def a: ({type A[+A, B] = Either[A, Option[B]]})#A"
    val res = "def a: Lambda[(`+A`, B) => Either[A, Option[B]]]"
    testFix(text, res)
  }

  def testHigherKind(): Unit = {
    val code = s"def a: $START({type A[A, B[_]] = B[A]})#A$END"
    checkTextHasError(code)
    val text = "def a: ({type A[A, B[_]] = B[A]})#A"
    val res = "def a: Lambda[(A, B[_]) => B[A]]"
    testFix(text, res)
  }

  def testBound(): Unit = {
    val code = s"def a: $START({type B[A <: Any] = (A, A)})#B$END"
    checkTextHasError(code)
    val text = "def a: ({type B[A <: Any] = (A, A)})#B"
    val res = "def a: Lambda[`A <: Any` => (A, A)]"
    testFix(text, res)
  }

  def testTwoBound(): Unit = {
    val code = s"def a: $START({type B[A >: Int <: Any] = (A, A)})#B$END"
    checkTextHasError(code)
    val text = "def a: ({type B[A >: Int <: Any] = (A, A)})#B"
    val res = "def a: Lambda[`A >: Int <: Any` => (A, A)]"
    testFix(text, res)
  }

  def testMultipleVariantBounds(): Unit = {
    val code = s"def a: $START({type B[-C >: Int, +A <: Any] = (A, A, C)})#B$END"
    checkTextHasError(code)
    val text = "def a: ({type B[-C >: Int, +A <: Any] = (A, A, C)})#B"
    val res = "def a: Lambda[(`-C >: Int`, `+A <: Any`) => (A, A, C)]"
    testFix(text, res)
  }

  def testParameterizedBounds(): Unit = {
    val code = s"def a: ({type B[C >: List[Int], +A <: Any] = (A, A, C)})#B"
    checkTextHasNoErrors(code)
  }

  def testMixingBounds(): Unit = {
    val code = s"def a: ({type B[C >: Int with String] = (C, C)})#B"
    checkTextHasNoErrors(code)
  }

  def testExistentialBounds(): Unit = {
    val code = s"def a: ({type B[C >: Array[X] forSome { type x }] = (C, C)})#B"
    checkTextHasNoErrors(code)
  }

  def testAliasNoParam(): Unit = {
    val code = "def a: ({type Lambda$ = String})#Lambda$"
    checkTextHasNoErrors(code)
  }

  def testTupleInline(): Unit = {
    val code = s"def a: $START({type R[A] = Tuple2[A, Double]})#R$END"
    checkTextHasError(code)
    val text = "def a: ({type R[A] = Tuple2[A, Double]})#R"
    val res = "def a: Tuple2[?, Double]"
    testFix(text, res)
  }

  def testHigherKindInline(): Unit = {
    val code = s"def d: $START({type R[F[_], +B] = Either[F, B]})#R$END"
    checkTextHasError(code)
    val text = "def d: ({type R[F[_], +B] = Either[F, B]})#R"
    val res = "def d: Either[?[_], +?]"
    testFix(text, res)
  }

  def testTypeBoundsNoInline(): Unit = {
    val code = s"def w: $START({type R[A <: String] = List[A]})#R$END"
    checkTextHasError(code)
    val text = "def w: ({type R[A <: String] = List[A]})#R"
    val res = "def w: Lambda[`A <: String` => List[A]]"
    testFix(text, res)
  }
}
