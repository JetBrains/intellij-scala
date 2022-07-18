package org.jetbrains.plugins.scala
package lang.autoImport

import com.intellij.codeInsight.JavaProjectCodeInsightSettings
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.autoImport.quickFix.{ElementToImport, ScalaImportElementFix}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions.StringExt
import org.junit.Assert.{assertEquals, fail}
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

@Category(Array(classOf[LanguageTests]))
abstract class ImportElementFixTestBase[Psi <: PsiElement : ClassTag]
  extends ScalaLightCodeInsightFixtureTestAdapter with ScalaFiles {

  def createFix(element: Psi): Option[ScalaImportElementFix[_ <: ElementToImport]]

  def checkElementsToImport(fileText: String, expectedQNames: String*): Unit = {
    val fix = configureAndCreateFix(fileText)
    assertEquals("Wrong elements to import found: ", expectedQNames, fix.elements.map(_.qualifiedName))
  }

  def checkNoImportFix(fileText: String): Unit = {
    val fix = configureAndCreateFix(fileText)
    assertEquals(s"Some elements to import found ${fix.elements.map(_.qualifiedName)}", Seq.empty, fix.elements)
  }

  def doTest(fileText: String, expectedText: String, selected: String): Unit = {
    val fix = configureAndCreateFix(fileText)
    val action = fix.createAddImportAction(getEditor)

    fix.elements.find(_.qualifiedName == selected) match {
      case None       => fail(s"No elements found with qualified name $selected")
      case Some(elem) => action.addImportTestOnly(elem)
    }
    assertEquals("Result doesn't match expected text", expectedText.withNormalizedSeparator.trim, getFile.getText.withNormalizedSeparator.trim)
  }

  private def configureAndCreateFix(fileText: String): ScalaImportElementFix[_ <: ElementToImport] = {
    val file = configureFromFileText(fileType, fileText)
    val clazz = implicitly[ClassTag[Psi]].runtimeClass.asInstanceOf[Class[Psi]]
    val element = PsiTreeUtil.findElementOfClassAtOffset(file, getEditorOffset, clazz, false)
    createFix(element).getOrElse(throw NoFixException(element))
  }

  protected def withExcluded(qNames: String*)(body: => Unit): Unit =
    ImportElementFixTestBase.withExcluded(getProject, qNames)(body)

  private case class NoFixException(element: PsiElement)
    extends AssertionError(s"Import fix not found for ${element.getText}")
}

object ImportElementFixTestBase {

  def withExcluded(project: Project, qNames: Seq[String])(body: => Unit): Unit = {
    val settings = JavaProjectCodeInsightSettings.getSettings(project)
    val originalNames = settings.excludedNames
    settings.excludedNames = qNames.asJava

    try body
    finally settings.excludedNames = originalNames
  }

}
