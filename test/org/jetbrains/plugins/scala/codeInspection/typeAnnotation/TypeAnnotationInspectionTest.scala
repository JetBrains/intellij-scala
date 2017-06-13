package org.jetbrains.plugins.scala
package codeInspection
package typeAnnotation

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil

class TypeAnnotationInspectionTest extends ScalaQuickFixTestBase {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}
  import TypeAnnotationInspectionTest.createTestText

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[TypeAnnotationInspection]

  override protected val description: String =
    TypeAnnotationInspection.Description

  def testPublicValue(): Unit = testQuickFix(
    text = s"val ${START}foo$END",
    expected = s"val foo: Foo"
  )

  def testProtectedValue(): Unit = testQuickFix(
    text = s"protected val ${START}foo$END",
    expected = s"protected val foo: Foo"
  )

  def testPrivateValue(): Unit = checkTextHasNoErrors(
    createTestText("private val foo")
  )

  def testPublicVariable(): Unit = testQuickFix(
    text = s"var ${START}foo$END",
    expected = s"var foo: Foo"
  )

  def testProtectedVariable(): Unit = testQuickFix(
    text = s"protected var ${START}foo$END",
    expected = s"protected var foo: Foo"
  )

  def testPrivateVariable(): Unit = checkTextHasNoErrors(
    createTestText("private var foo")
  )

  def testPublicMethod(): Unit = testQuickFix(
    text = s"def ${START}foo$END()",
    expected = s"def foo(): Foo"
  )

  def testProtectedMethod(): Unit = testQuickFix(
    text = s"protected def ${START}foo$END()",
    expected = s"protected def foo(): Foo"
  )

  def testPrivateMethod(): Unit = checkTextHasNoErrors(
    createTestText("private def foo()")
  )

  private def testQuickFix(text: String, expected: String): Unit =
    testQuickFix(createTestText(text), createTestText(expected), AddTypeAnnotationQuickFix.Name)
}

object TypeAnnotationInspectionTest {

  private def createTestText(text: String): String =
    s"""
       |class Foo
       |
       |class Bar {
       |  $text = new Foo
       |}
       |
       |new Bar
     """.stripMargin
}