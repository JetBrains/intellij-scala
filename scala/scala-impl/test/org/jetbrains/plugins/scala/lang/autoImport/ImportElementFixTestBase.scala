package org.jetbrains.plugins.scala.lang.autoImport

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportElementFix
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.Assert.assertEquals

import scala.reflect.ClassTag

abstract class ImportElementFixTestBase[Psi <: PsiElement : ClassTag] extends ScalaLightCodeInsightFixtureTestAdapter {

  def createFix(element: Psi): Option[ScalaImportElementFix]

  def checkElementsToImport(fileText: String, expectedQNames: String*): Unit = {
    val file = configureFromFileText(fileText, ScalaFileType.INSTANCE)
    val clazz = implicitly[ClassTag[Psi]].runtimeClass.asInstanceOf[Class[Psi]]
    val element = PsiTreeUtil.findElementOfClassAtOffset(file, getEditorOffset, clazz, false)
    val fix = createFix(element).getOrElse(throw new AssertionError(s"Import fix not found for ${element.getText}"))
    assertEquals("Wrong elements to import found: ", expectedQNames, fix.elements.map(_.qualifiedName))
  }
}

