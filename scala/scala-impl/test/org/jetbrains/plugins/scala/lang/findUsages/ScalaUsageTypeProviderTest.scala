package org.jetbrains.plugins.scala
package lang
package findUsages

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category

@Category(Array(classOf[LanguageTests]))
class ScalaUsageTypeProviderTest extends ScalaFixtureTestCase {
  private val usageTypeProvider = new ScalaUsageTypeProvider

  private def doTest(source: String, typed: String): Unit = {
    myFixture.configureByText("dummy.scala", source)

    val result = {
      val outputBuilder = new StringBuilder

      def print(element: PsiElement, intention: String = ""): Unit = {
        outputBuilder ++= intention
        outputBuilder ++= element.getNode.getElementType.toString
        element match {
          case named: ScNamedElement =>
            outputBuilder += '['
            outputBuilder ++= named.name
            outputBuilder += ']'
          case ref: ScReference =>
            outputBuilder += '['
            outputBuilder ++= ref.refName
            outputBuilder += ']'
          case _ =>
        }
        for (usageType <- usageTypeProvider.getUsageType(element).nullSafe) {
          outputBuilder ++= " -> "
          outputBuilder ++= usageType.toString
        }
        outputBuilder += '\n'
        element.getChildren.foreach(print(_, intention + "  "))
      }
      print(myFixture.getFile)
      outputBuilder.result()
    }

    assertEquals(typed, result)
  }


  def test_assignment(): Unit = doTest(
    """
      |class Test {
      |  var x = 0
      |  x = 42
      |  def method(): Unit = {
      |    x = 99
      |  }
      |}
      |""".stripMargin.withNormalizedSeparator,
    """scala.FILE
      |  WHITE_SPACE
      |  ScClass[Test]
      |    annotations
      |    modifiers
      |    primary constructor
      |      annotations
      |      modifiers
      |      parameter clauses
      |    extends block
      |      template body -> Value read
      |        variable definition -> Value read
      |          annotations -> Value read
      |          modifiers -> Value read
      |          pattern list -> Value read
      |            reference pattern[x] -> Value read
      |          IntegerLiteral -> Value read
      |        assign statement -> Value read
      |          Reference expression[x] -> Value write
      |          IntegerLiteral -> Value read
      |        function definition[method] -> Value read
      |          annotations -> Value read
      |          modifiers -> Value read
      |          parameter clauses -> Value read
      |            parameter clause -> Value read
      |          simple type -> Method return type
      |            reference[Unit] -> Method return type
      |          block of expressions -> Value read
      |            { -> Value read
      |            WHITE_SPACE -> Value read
      |            assign statement -> Value read
      |              Reference expression[x] -> Value write
      |              IntegerLiteral -> Value read
      |            WHITE_SPACE -> Value read
      |            } -> Value read
      |  WHITE_SPACE
      |""".stripMargin.withNormalizedSeparator
  )
}
