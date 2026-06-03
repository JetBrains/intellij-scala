package org.jetbrains.plugins.scala.lang.findUsages

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType
import org.junit.Assert.assertEquals

abstract class ScalaUsageTypeProviderTestBase extends ScalaFixtureTestCase {
  private val usageTypeProvider = new ScalaUsageTypeProvider

  protected def doTest(source: String, typed: String): Unit = {
    myFixture.configureByText("dummy.scala", source)

    val result = {
      val outputBuilder = new StringBuilder

      def print(element: PsiElement, indentation: String = ""): Unit = {
        outputBuilder ++= indentation
        val elementTypeText = element.getNode.getElementType match {
          case _: ScStubFileElementType => "scala.FILE" //use same value for Scala2 and Scala3 to reuse test data
          case t => t.toString
        }
        outputBuilder ++= elementTypeText
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

        val children = element.getChildren
        //filter out whitespaces and empty elements (e.g. empty annotations)
        val childrenVisible = children.filter(_.getText.trim.nonEmpty)
        childrenVisible.foreach(print(_, indentation + "  "))
      }

      print(myFixture.getFile)
      outputBuilder.result()
    }

    assertEquals(typed, result)
  }
}
