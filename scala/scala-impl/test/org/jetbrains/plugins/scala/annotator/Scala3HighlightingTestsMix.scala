package org.jetbrains.plugins.scala.annotator

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaVersion

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
}
