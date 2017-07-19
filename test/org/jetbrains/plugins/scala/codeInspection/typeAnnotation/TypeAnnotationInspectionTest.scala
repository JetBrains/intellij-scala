package org.jetbrains.plugins.scala
package codeInspection
package typeAnnotation

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

abstract class TypeAnnotationInspectionTest extends ScalaQuickFixTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[TypeAnnotationInspection]

  override protected val description: String =
    TypeAnnotationInspection.Description

  protected def testQuickFix(text: String, expected: String): Unit =
    testQuickFix(text, expected, AddTypeAnnotationQuickFix.Name)
}

class MembersTypeAnnotationInspectionTest extends TypeAnnotationInspectionTest {

  def testPublicValue(): Unit = testQuickFix(
    text = s"val ${START}foo$END",
    expected = s"val foo: Foo"
  )

  def testProtectedValue(): Unit = testQuickFix(
    text = s"protected val ${START}foo$END",
    expected = s"protected val foo: Foo"
  )

  def testPrivateValue(): Unit =
    checkTextHasNoErrors("private val foo")

  def testImplicitValue(): Unit = testQuickFix(
    text = s"implicit val ${START}foo$END",
    expected = s"implicit val foo: Foo"
  )

  def testPublicVariable(): Unit = testQuickFix(
    text = s"var ${START}foo$END",
    expected = s"var foo: Foo"
  )

  def testProtectedVariable(): Unit = testQuickFix(
    text = s"protected var ${START}foo$END",
    expected = s"protected var foo: Foo"
  )

  def testPrivateVariable(): Unit =
    checkTextHasNoErrors("private var foo")

  def testImplicitVariable(): Unit = testQuickFix(
    text = s"implicit var ${START}foo$END",
    expected = s"implicit var foo: Foo"
  )

  def testPublicMethod(): Unit = testQuickFix(
    text = s"def ${START}foo$END()",
    expected = s"def foo(): Foo"
  )

  def testProtectedMethod(): Unit = testQuickFix(
    text = s"protected def ${START}foo$END()",
    expected = s"protected def foo(): Foo"
  )

  def testPrivateMethod(): Unit =
    checkTextHasNoErrors("private def foo()")

  def testImplicitMethod(): Unit = testQuickFix(
    text = s"implicit def ${START}foo$END()",
    expected = s"implicit def foo(): Foo"
  )

  override protected def createTestText(text: String): String =
    s"""
       |class Foo
       |
       |class Bar {
       |  $text = fooImpl()
       |
       |  private def fooImpl(): Foo = new Foo
       |}
       |
       |new Bar
     """.stripMargin
}

class LocalTypeAnnotationInspectionTest extends TypeAnnotationInspectionTest {

  def testLocal(): Unit =
    checkTextHasNoErrors("val foo = new Foo")

  def testLocalImplicit(): Unit = testQuickFix(
    text = s"implicit val ${START}foo$END = new Foo",
    expected = "implicit val foo: Foo = new Foo"
  )

  def testObject(): Unit =
    checkTextHasNoErrors("val foo = new Foo")

  def testImplicitObject(): Unit = testQuickFix(
    text = s"implicit val ${START}foo$END = Foo",
    expected = "implicit val foo: Foo.type = Foo"
  )

  override protected def createTestText(text: String): String =
    s"""
       |class Foo {
       |
       |  def foo(): Unit = {
       |    $text
       |  }
       |}
       |
       |object Foo
       |
       |new Foo
     """.stripMargin
}

class SimpleTypeAnnotationInspectionTest extends TypeAnnotationInspectionTest {

  def testPublicValue(): Unit =
    checkTextHasNoErrors("val foo")

  def testProtectedValue(): Unit =
    checkTextHasNoErrors(s"protected val foo")

  def testPrivateValue(): Unit =
    checkTextHasNoErrors(s"private val foo")

  def testImplicitValue(): Unit = testQuickFix(
    text = s"implicit val ${START}foo$END",
    expected = s"implicit val foo: Foo"
  )

  def testPublicVariable(): Unit =
    checkTextHasNoErrors("var foo")

  def testProtectedVariable(): Unit =
    checkTextHasNoErrors("protected var foo")

  def testPrivateVariable(): Unit =
    checkTextHasNoErrors("private var foo")

  def testImplicitVariable(): Unit = testQuickFix(
    text = s"implicit var ${START}foo$END",
    expected = s"implicit var foo: Foo"
  )

  def testPublicMethod(): Unit =
    checkTextHasNoErrors("def foo")

  def testProtectedMethod(): Unit =
    checkTextHasNoErrors("protected def foo")

  def testPrivateMethod(): Unit =
    checkTextHasNoErrors("private def foo")

  def testImplicitMethod(): Unit = testQuickFix(
    text = s"implicit def ${START}foo$END",
    expected = s"implicit def foo: Foo"
  )

  override protected def createTestText(text: String): String =
    s"""
       |class Foo {
       |
       |  $text = new Foo
       |}
       |
       |new Foo
     """.stripMargin
}

class ObjectTypeAnnotationInspectionTest extends TypeAnnotationInspectionTest {

  def testPublicValue(): Unit = testQuickFix(
    text = s"val ${START}foo$END",
    expected = s"val foo: Foo.type"
  )

  def testProtectedValue(): Unit = testQuickFix(
    text = s"protected val ${START}foo$END",
    expected = s"protected val foo: Foo.type"
  )

  def testPrivateValue(): Unit =
    checkTextHasNoErrors("private val foo")

  def testImplicitValue(): Unit = testQuickFix(
    text = s"implicit val ${START}foo$END",
    expected = s"implicit val foo: Foo.type"
  )

  def testPublicVariable(): Unit = testQuickFix(
    text = s"var ${START}foo$END",
    expected = s"var foo: Foo.type"
  )

  def testProtectedVariable(): Unit = testQuickFix(
    text = s"protected var ${START}foo$END",
    expected = s"protected var foo: Foo.type"
  )

  def testPrivateVariable(): Unit =
    checkTextHasNoErrors("private var foo")

  def testImplicitVariable(): Unit = testQuickFix(
    text = s"implicit var ${START}foo$END",
    expected = s"implicit var foo: Foo.type"
  )

  def testPublicMethod(): Unit = testQuickFix(
    text = s"def ${START}foo$END()",
    expected = s"def foo(): Foo.type"
  )

  def testProtectedMethod(): Unit = testQuickFix(
    text = s"protected def ${START}foo$END()",
    expected = s"protected def foo(): Foo.type"
  )

  def testPrivateMethod(): Unit =
    checkTextHasNoErrors("private def foo()")

  def testImplicitMethod(): Unit = testQuickFix(
    text = s"implicit def ${START}foo$END()",
    expected = s"implicit def foo(): Foo.type"
  )

  override protected def createTestText(text: String): String =
    s"""
       |class Foo {
       |
       |  $text = Foo
       |}
       |
       |object Foo
       |
       |new Foo
     """.stripMargin
}