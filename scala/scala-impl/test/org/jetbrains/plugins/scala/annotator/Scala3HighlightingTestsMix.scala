package org.jetbrains.plugins.scala.annotator

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.Message.Error

import scala.jdk.CollectionConverters.ListHasAsScala

/**
 * Contains highlighting tests, for which no better test class was found
 */
class Scala3HighlightingTestsMix extends ScalaHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  override def assertNoErrors(@Language("Scala 3") code: String): Unit =
    assertErrors(code, Nil: _*)

  override protected def messagesFromScalaCode(file: PsiFile): List[Message] = {
    getFixture.openFileInEditor(file.getVirtualFile)

    //using "true" editor highlighting (see TO-DO comment in ScalaHighlightingTestLike)
    val allInfo = getFixture.doHighlighting().asScala.toList
    val errors = allInfo.filter(_.`type`.getSeverity(null) == HighlightSeverity.ERROR)
    errors.map { info => Message.Error(info.getText, info.getDescription) }
  }

  //SCL-21604
  def testAccessCompanionObjectMembersInPresenceOfAnonymousUsingParameterWithCompanionType(): Unit = {
    assertNoErrors(
      s"""type MyClass = Int
         |object MyClass:
         |  def test(): String = ""
         |
         |def foo(using MyClass): Unit = {
         |  summon[MyClass]
         |  MyClass.test()
         |}
         |""".stripMargin
    )
  }

  //SCL-21604, SCL-21321
  def testAccessCompanionObjectMembersInPresenceOfAnonymousUsingParameterWithCompanionType_CompanionObjectUnresolved(): Unit = {
    assertMessagesText(
      """type MyClass = Int
        |
        |def foo(using MyClass): Unit = {
        |  summon[MyClass]
        |  MyClass.test()
        |}
        |""".stripMargin,
      """Error(MyClass,Cannot resolve symbol MyClass)
        |""".stripMargin
    )
  }

  //SCL-21834
  def testMultipleAnonymousParameters(): Unit = {
    assertNoErrors(
      """case class Company(name: String)
        |case class SalesRep(name: String)
        |
        |case class Invoice(customer: String)(using Company, SalesRep):
        |  override def toString = s"${summon[Company].name} / ${summon[SalesRep].name} - Customer: $customer"
        |
        |@main def test(): Unit =
        |  given Company = Company("Big Corp")
        |  given SalesRep = SalesRep("John")
        |  println(Invoice("Peter LTD"))
        |""".stripMargin
    )
  }

  // SCL-21795
  def testSetterWithUsingParameters(): Unit = {
    val code =
      """
        |class Foo {
        |  private var _x = 1
        |  def x(using String): Int = _x
        |  def x_=(y: Int)(using String): Unit = _x = y
        |}
        |
        |object Foo {
        |  def main(args: Array[String]): Unit = {
        |    val foo = Foo()
        |    given String = "foo"
        |    foo.x = 5
        |  }
        |}
        |""".stripMargin

    assertNothing(errorsFromScalaCode(code))
  }


  def testTypeMismatchUnappliedMethod(): Unit = {
    assertMessages(errorsFromScalaCode("given Int = 3; def f(int: Int)(using Int): Boolean = true; val v: Int = f(1)"))(
      Error("f(1)", "Expression of type Boolean doesn't conform to expected type Int")
    )
  }

  def testStdLibPatches(): Unit = assertNothing(errorsFromScalaCode(
    s"""import scala.language.dynamics
       |import _root_.scala.language.dynamics
       |
       |import scala.language.experimental.macros
       |import _root_.scala.language.experimental.macros
       |
       |import scala.language.noAutoTupling
       |import _root_.scala.language.noAutoTupling
       |
       |import scala.language.experimental.namedTypeArguments
       |import _root_.scala.language.experimental.namedTypeArguments""".stripMargin))
}
