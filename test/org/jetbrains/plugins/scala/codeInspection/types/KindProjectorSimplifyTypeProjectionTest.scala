package org.jetbrains.plugins.scala.codeInspection.types

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.typeLambdaSimplify.KindProjectorSimplifyTypeProjectionInspection
import org.jetbrains.plugins.scala.codeInspection.{InspectionBundle, ScalaLightInspectionFixtureTestAdapter}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 7/6/15
 */
class KindProjectorSimplifyTypeProjectionTest extends ScalaLightInspectionFixtureTestAdapter {
  override protected def classOfInspection: Class[_ <: LocalInspectionTool] = classOf[KindProjectorSimplifyTypeProjectionInspection]

  override protected def annotation: String = InspectionBundle.message("kind.projector.simplify.type")

  override protected def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val newSettings = defaultProfile.getSettings
    newSettings.plugins :+= "kind-projector"
    defaultProfile.setSettings(newSettings)
  }

  def testEither(): Unit = {
    val code = s"def a: $START({type A[Beta] = Either[Int, Beta]})#A$END"
    check(code)
    val text = "def a: ({type A[Beta] = Either[Int, Beta]})#A"
    val res = "def a: Lambda[Beta => Either[Int, Beta]]"
    testFix(text, res)
  }

  def testTwoParameters(): Unit = {
    val code = s"def a: $START({type A[-Alpha, +Gamma] = Function2[Alpha, String, Gamma]})#A$END"
    check(code)
    val text = "def a: ({type A[-Alpha, +Gamma] = Function2[Alpha, String, Gamma]})#A"
    val res = "def a: Lambda[(`-Alpha`, `+Gamma`) => (Alpha, String) => Gamma]"
    testFix(text, res)
  }

  def testRepeatedParams(): Unit = {
    val code = s"def a: $START({type A[A] = (A, A)})#A$END"
    check(code)
    val text = "def a: ({type A[A] = (A, A)})#A"
    val res = "def a: Lambda[A => (A, A)]"
    testFix(text, res)
  }

  def testCovariant(): Unit = {
    val code = s"def a: $START({type A[+A, B] = Either[A, Option[B]]})#A$END"
    check(code)
    val text = "def a: ({type A[+A, B] = Either[A, Option[B]]})#A"
    val res = "def a: Lambda[(`+A`, B) => Either[A, Option[B]]]"
    testFix(text, res)
  }

  def testHigherKind(): Unit = {
    val code = s"def a: $START({type A[A, B[_]] = B[A]})#A$END"
    check(code)
    val text = "def a: ({type A[A, B[_]] = B[A]})#A"
    val res = "def a: Lambda[(A, B[_]) => B[A]]"
    testFix(text, res)
  }

  def testBound(): Unit = {
    val code = s"def a: $START({type B[A <: Any] = (A, A)})#B$END"
    check(code)
    val text = "def a: ({type B[A <: Any] = (A, A)})#B"
    val res = "def a: Lambda[`A <: Any` => (A, A)]"
    testFix(text, res)
  }

  def testTwoBound(): Unit = {
    val code = s"def a: $START({type B[A >: Int <: Any] = (A, A)})#B$END"
    check(code)
    val text = "def a: ({type B[A >: Int <: Any] = (A, A)})#B"
    val res = "def a: Lambda[`A >: Int <: Any` => (A, A)]"
    testFix(text, res)
  }

  def testMultipleVariantBounds(): Unit = {
    val code = s"def a: $START({type B[-C >: Int, +A <: Any] = (A, A, C)})#B$END"
    check(code)
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

  def testFix(text: String, res: String): Unit = testFix(text, res, annotation)
}
