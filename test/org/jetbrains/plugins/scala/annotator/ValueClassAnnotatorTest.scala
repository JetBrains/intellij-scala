package org.jetbrains.plugins.scala.annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
  * @author ilinum
  */
class ValueClassAnnotatorTest extends SimpleTestCase {

  def testPrimaryConstructorParameters(): Unit = {
    val code =
      """
        |class Foo(private val b: Int) extends AnyVal
        |class Bar(val g: Int) extends AnyVal
        |case class Baz(a: Int) extends AnyVal
        |class Blargle(s: Int) extends AnyVal
        |class Blargle2(z: Int, b: Int) extends AnyVal
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("s: Int", NonPrivateValParameter()) :: Error("Blargle2", OnlyOneParameter()) :: Nil =>
    }
  }

  def testSecondaryConstructors(): Unit = {
    val code =
      """
        |class Foo(val b: Int) extends AnyVal {
        |  def this() {
        |    this(-1)
        |  }
        |}
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("this", SecondaryConstructor()) :: Nil =>
    }
  }

  def testNestedObjects(): Unit = {
    val code =
      """
        |class Foo(val s: Int) extends AnyVal {
        |  trait Inner
        |}
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("Inner", InnerObjects()) :: Nil =>
    }
  }

  def testRedefineEqualsHashCode(): Unit = {
    val code =
      """
        |class Foo(val a: Int) extends AnyVal {
        |  def equals: Int = 2
        |  def hashCode: Double = 2.0
        |}
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("equals", RedefineEqualsHashCode()) :: Error("hashCode", RedefineEqualsHashCode()) :: Nil =>
    }
  }

  def testOnlyDefMembers(): Unit = {
    val code =
      """
        |class Foo(val a: Int) extends AnyVal {
        |  def foo: Double = 2.0
        |  val x = 10
        |}
      """.stripMargin
    assertMatches(messages(code)) {
      case Error("x", ValueClassCanNotHaveFields()) :: Nil =>
    }
  }

  def messages(@Language(value = "Scala") code: String): List[Message] = {
    val file: ScalaFile = code.parse

    val annotator = new ScalaAnnotator() {}
    val mock = new AnnotatorHolderMock(file)
    file.depthFirst.foreach(annotator.annotate(_, mock))
    mock.errorAnnotations
  }

  val NonPrivateValParameter = ContainsPattern("Value classes can have only one non-private val parameter")
  val OnlyOneParameter = ContainsPattern("Value classes can have only one parameter")
  val SecondaryConstructor = ContainsPattern("Secondary constructors are not allowed in value classes")
  val InnerObjects = ContainsPattern("Value classes cannot have nested classes, objects or traits")
  val RedefineEqualsHashCode = ContainsPattern("Value classes cannot redefine equals and hashCode")
  val ValueClassCanNotHaveFields = ContainsPattern("Field definitions are not allowed in value classes")
}
