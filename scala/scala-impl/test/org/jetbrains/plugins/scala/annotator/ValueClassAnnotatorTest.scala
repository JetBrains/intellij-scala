package org.jetbrains.plugins.scala
package annotator

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class ValueClassAnnotatorTest extends SimpleTestCase with ScalaHighlightingTestLike {
  import Message._

  override protected def getFixture: CodeInsightTestFixture = this.fixture

  def testPrimaryConstructorParameters(): Unit = {
    val code =
      """final class A1(a1: Double) extends AnyVal
        |final class A2(a2: Double, anotherParam: Int) extends AnyVal
        |final class A3(val a3: Double) extends AnyVal
        |final class A4(val a4: Double, anotherParam: Int) extends AnyVal
        |final class A5(var a5: Double) extends AnyVal
        |
        |final case class B1(b1: Double) extends AnyVal
        |final case class B2(b2: Double, anotherParam: Int) extends AnyVal
        |final case class B3(val b3: Double) extends AnyVal
        |final case class B4(val b4: Double, anotherParam: Int) extends AnyVal
        |final case class B5(var b5: Double) extends AnyVal
        |""".stripMargin
    assertMessagesText(code,
      """Error(a1: Double,Value classes can have only one non-private val parameter)
        |Error(A2,Value classes can have only one parameter)
        |Error(A4,Value classes can have only one parameter)
        |Error(var a5: Double,Value classes can have only one non-private val parameter)
        |
        |Error(B2,Value classes can have only one parameter)
        |Error(B4,Value classes can have only one parameter)
        |Error(var b5: Double,Value classes can have only one non-private val parameter)
        |""".stripMargin
    )
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
    messagesFromScalaCode(file)
  }

  val NonPrivateValParameter = ContainsPattern("Value classes can have only one non-private val parameter")
  val OnlyOneParameter = ContainsPattern("Value classes can have only one parameter")
  val SecondaryConstructor = ContainsPattern("Secondary constructors are not allowed in value classes")
  val InnerObjects = ContainsPattern("Value classes cannot have nested classes, objects or traits")
  val RedefineEqualsHashCode = ContainsPattern("Value classes cannot redefine equals and hashCode")
  val ValueClassCanNotHaveFields = ContainsPattern("Field definitions are not allowed in value classes")
}
