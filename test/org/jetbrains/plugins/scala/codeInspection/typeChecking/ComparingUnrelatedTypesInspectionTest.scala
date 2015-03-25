package org.jetbrains.plugins.scala
package codeInspection.typeChecking

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaLightInspectionFixtureTestAdapter

/**
 * Nikolay.Tropin
 * 9/26/13
 */
class ComparingUnrelatedTypesInspectionTest extends ScalaLightInspectionFixtureTestAdapter {
  protected def classOfInspection: Class[_ <: LocalInspectionTool] = classOf[ComparingUnrelatedTypesInspection]
  protected val annotation: String = ComparingUnrelatedTypesInspection.inspectionName

  def testWeakConformance() {
    val text1 = s"""val a = 0
                 |val b: Short = 1
                 |${START}b == a$END"""
    val text2 = s"""val a = 0
                  |val b = 1.0
                  |${START}b != a$END"""
    val text3 = s"""val a = 0.0
                  |val b: Byte = 100
                  |${START}a == b$END"""
    val text4 = s"${START}1 == 1.0$END"
    checkTextHasNoErrors(text1)
    checkTextHasNoErrors(text2)
    checkTextHasNoErrors(text3)
    checkTextHasNoErrors(text4)
  }

  def testValueTypes() {
    val text1 = s"""val a = true
                 |val b = 1
                 |${START}b == a$END"""
    val text2 = s"""val a = true
                 |val b = 0.0
                 |${START}a != b$END"""
    val text3 = s"${START}true != 0$END"
    val text4: String = s"${START}1.isInstanceOf[Boolean]$END"
    checkTextHasError(text1)
    checkTextHasError(text2)
    checkTextHasError(text3)
    checkTextHasError(text4)

  }

  def testString() {
    val text1 = s"""val a = "a"
                 |val b = Array('a')
                 |${START}b == a$END"""
    val text2 = s"""val a = "0"
                 |val b = 0
                 |${START}a == b$END"""
    val text3 = s"""val s = "s"
                  |${START}s == 's'$END"""
    val text4 = s"""val a = "a"
                  |val b: CharSequence = null
                  |${START}b != a$END"""
    checkTextHasError(text1)
    checkTextHasError(text2)
    checkTextHasError(text3)
    checkTextHasNoErrors(text4)
  }

  def testInheritors() {
    val text1 = s"""val a = scala.collection.Iterable(1)
                 |val b = List(0)
                 |${START}b == a$END"""
    val text2 = s"""case class A(i: Int)
                   |final class B extends A(1)
                   |val a: A = A(0)
                   |val b: B = new B
                   |${START}a == b$END"""
    val text3 = """trait A
                  |object B extends A
                  |B.isInstanceOf[A]"""
    checkTextHasNoErrors(text1)
    checkTextHasNoErrors(text2)
    checkTextHasNoErrors(text3)
  }

  def testFinal() {
    val text1 = s"""case class A(i: Int)
                   |class B extends A(1)
                   |val a: A = A(0)
                   |val b: B = new B
                   |${START}a == b$END"""
    val text2 = s"""final class A extends Serializable
                  |final class B extends Serializable
                  |val a: A = new A
                  |val b: B = new B
                  |${START}a == b$END"""
    val text3 = s"""final class A extends Serializable
                   |final class B extends Serializable
                   |val a: A = new A
                   |${START}a.isInstanceOf[B]$END"""
    checkTextHasNoErrors(text1)
    checkTextHasError(text2)
    checkTextHasError(text3)
  }

  def testTraits() {
    val text1 = s"""trait A
                  |trait B
                  |val a: A = _
                  |val b: B = _
                  |${START}a == b$END"""
    checkTextHasNoErrors(text1)
  }

  def testObject() {
    val text1 = s"""trait A
                  |object B extends A
                  |val a: A = _
                  |${START}a == B$END"""
    val text2 = s"""trait A
                  |object B extends A
                  |class C extends A
                  |val c = new C
                  |${START}c == B$END"""
    val text3 = s"""trait A
                  |object B extends A
                  |class C extends A
                  |val c: A = new C
                  |${START}c != B$END"""
    checkTextHasNoErrors(text1)
    checkTextHasError(text2)
    checkTextHasNoErrors(text3)
  }

  def testBoxedTypes() {
    val text1 = """val i = new java.lang.Integer(0)
                  |i == 100"""
    val text2 = """val b = new java.lang.Boolean(false)
                  |b equals true"""
    val text3 = "def test(i: Integer) = if (i == null) \"foo\" else \"bar\""
    checkTextHasNoErrors(text1)
    checkTextHasNoErrors(text2)
    checkTextHasNoErrors(text3)
  }

  def testExistential(): Unit = {
    checkTextHasNoErrors("Seq(1).isInstanceOf[List[_])")
    checkTextHasError(s"${START}Some(1).isInstanceOf[List[_]]$END")
    checkTextHasNoErrors("def foo(x: Some[_]) { x == Some(1) }")
    checkTextHasError(s"def foo(x: Some[_]) { ${START}x == Seq(1)$END }")
  }
}
