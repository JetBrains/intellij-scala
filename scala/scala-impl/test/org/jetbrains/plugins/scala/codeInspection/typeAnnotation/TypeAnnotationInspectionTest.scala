package org.jetbrains.plugins.scala
package codeInspection
package typeAnnotation

import com.intellij.codeInspection.LocalInspectionTool

abstract class TypeAnnotationInspectionTest extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[TypeAnnotationInspection]

  override protected val description: String =
    ScalaInspectionBundle.message("type.annotation.required.for", "")

  override protected def descriptionMatches(s: String): Boolean =
    Option(s).exists(_.startsWith(description))

  protected def testQuickFix(text: String, expected: String): Unit =
    testQuickFix(text, expected, ScalaInspectionBundle.message("add.type.annotation"))
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
     """.stripMargin
}

class MembersTypeAnnotationInspectionTest_Scala3 extends TypeAnnotationInspectionTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  //Using "test1" + 42 in test data in order it's not just a primitive type in RHS
  def testExtensionMethods(): Unit = testQuickFix(
    text =
      s"""extension (s: String)
         |  def ${START}extension1${END} = "test1" + 42
         |  def extension2: String = "test2" + 42
         |  private def extension3 = "test3" + 42
         |""".stripMargin,
    expected =
      s"""extension (s: String)
         |  def extension1: String = "test1" + 42
         |  def extension2: String = "test2" + 42
         |  private def extension3 = "test3" + 42
         |""".stripMargin
  )
}

class SuperTypeAnnotationInspectionTest extends TypeAnnotationInspectionTest {

  def testImplementingMethod(): Unit = testQuickFix (
    text  =
      s"""
         |trait Test {
         |  def test(i: Int): Option[Int]
         |}
         |
         |class TestImpl extends Test {
         |  override def ${START}test$END(i: Int) = ???
         |}
        """.stripMargin,
    expected =
      """
        |trait Test {
        |  def test(i: Int): Option[Int]
        |}
        |
        |class TestImpl extends Test {
        |  override def test(i: Int): Option[Int] = ???
        |}
      """.stripMargin
  )

  def testImplementingValue(): Unit = testQuickFix (
    text  =
      s"""
         |trait Test {
         |  val foo: Option[Int]
         |}
         |
         |class TestImpl extends Test {
         |  override val ${START}foo$END = ???
         |}
        """.stripMargin,
    expected =
      s"""
         |trait Test {
         |  val foo: Option[Int]
         |}
         |
         |class TestImpl extends Test {
         |  override val foo: Option[Int] = ???
         |}
        """.stripMargin
  )

  // SCL-16081
  def testOverridingTypeAliasDef(): Unit = testQuickFix(
    text =
      s"""
        |trait Receiver {
        |  type Receive = PartialFunction[Any, Unit]
        |  def receive: Receive
        |}
        |
        |class MyReceiver extends Receiver {
        |  override def ${START}receive$END = {
        |    case _ =>
        |  }
        |}
        |""".stripMargin,
    expected =
      s"""
        |trait Receiver {
        |  type Receive = PartialFunction[Any, Unit]
        |  def receive: Receive
        |}
        |
        |class MyReceiver extends Receiver {
        |  override def receive: Receive = {
        |    case _ =>
        |  }
        |}
        |""".stripMargin
  )

  def testOverridingTypeAliasVal(): Unit = testQuickFix(
    text =
      s"""
         |trait Receiver {
         |  type Receive = PartialFunction[Any, Unit]
         |  val receive: Receive
         |}
         |
         |class MyReceiver extends Receiver {
         |  override val ${START}receive$END = {
         |    case _ =>
         |  }
         |}
         |""".stripMargin,
    expected =
      s"""
         |trait Receiver {
         |  type Receive = PartialFunction[Any, Unit]
         |  val receive: Receive
         |}
         |
         |class MyReceiver extends Receiver {
         |  override val receive: Receive = {
         |    case _ =>
         |  }
         |}
         |""".stripMargin
  )

  // opens a dialog and (here) selects first choice
  def testOverridingWithNewTypeAlias(): Unit = testQuickFix(
    text =
      s"""
         |trait Receiver {
         |  type Receive = PartialFunction[Any, Unit]
         |  def receive: Receive
         |}
         |
         |class MyReceiver extends Receiver {
         |  type MyReceive = PartialFunction[Any, Unit]
         |  override def ${START}receive$END = null.asInstanceOf[MyReceive]
         |}
         |""".stripMargin,
    expected =
      s"""
         |trait Receiver {
         |  type Receive = PartialFunction[Any, Unit]
         |  def receive: Receive
         |}
         |
         |class MyReceiver extends Receiver {
         |  type MyReceive = PartialFunction[Any, Unit]
         |  override def receive: MyReceive = null.asInstanceOf[MyReceive]
         |}
         |""".stripMargin
  )

  def testObjectEnum(): Unit = {
    testQuickFix(
      s"""
        |sealed trait EnumBase
        |object EnumElement extends EnumBase
        |
        |object Playground {
        |  def ${START}test$END = EnumElement
        |}
        |""".stripMargin,
      """
        |sealed trait EnumBase
        |object EnumElement extends EnumBase
        |
        |object Playground {
        |  def test: EnumBase = EnumElement
        |}
        |""".stripMargin
    )
  }

  def testIgnoredIfInheritsIgnored(): Unit = checkTextHasNoErrors(
    """
      |object junit {
      |  object framework {
      |    trait TestCase
      |  }
      |}
      |
      |class Test extends junit.framework.TestCase {
      |  val x = "test" + 3
      |}
      |""".stripMargin
  )

  def testIgnoredIfIsIgnored(): Unit = checkTextHasNoErrors(
    """
      |object junit {
      |  object framework {
      |    trait TestCase {
      |      val x = "test" + 3
      |    }
      |  }
      |}
      |""".stripMargin
  )


  // SCL-17115
  def testIgnoredIfSelfTypeInheritsIgnored(): Unit = checkTextHasNoErrors(
    """
      |object junit {
      |  object framework {
      |    trait TestCase
      |  }
      |}
      |
      |class Test { this: junit.framework.TestCase =>
      |  val x = "test" + 3
      |}
      |""".stripMargin
  )

  override protected def createTestText(text: String): String = text
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
     """.stripMargin
}

class OtherTypeAnnotationInspectionTest extends TypeAnnotationInspectionTest {
  // SCL-21725
  def test_not_type_alias(): Unit = testQuickFix(
    s"""
       |class A {
       |  type MyAlias = Int
       |  def foo: Int = ???
       |  def ${START}bar$END = foo
       |}
       |""".stripMargin,
    s"""
       |class A {
       |  type MyAlias = Int
       |  def foo: Int = ???
       |  def bar: Int = foo
       |}
       |""".stripMargin,
  )


  def test_better_type_alias(): Unit = testQuickFix(
    s"""
       |class A {
       |  type MyAlias1 = Int
       |  type MyAlias2 = Int
       |  type MyAlias3 = Int
       |  def foo: MyAlias2 = ???
       |  def ${START}bar$END = foo
       |}
       |""".stripMargin,
    s"""
       |class A {
       |  type MyAlias1 = Int
       |  type MyAlias2 = Int
       |  type MyAlias3 = Int
       |  def foo: MyAlias2 = ???
       |  def bar: MyAlias2 = foo
       |}
       |""".stripMargin,
  )
}

class FailingTypeAnnotationInspectionTest extends TypeAnnotationInspectionTest {
  def testErrorGivesAny(): Unit = testQuickFix(
    text =
      s"""
         |class Test {
         |  def test = unresolvedIdentifier
         |}
         |""".stripMargin,
    expected =
      s"""
         |class Test {
         |  def test: Any = unresolvedIdentifier
         |}
         |""".stripMargin
  )
}
