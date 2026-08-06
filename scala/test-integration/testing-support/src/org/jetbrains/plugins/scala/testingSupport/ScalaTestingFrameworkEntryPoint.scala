package org.jetbrains.plugins.scala.testingSupport

import com.intellij.codeInsight.TestFrameworks
import com.intellij.codeInspection.reference.{EntryPoint, RefElement}
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestTestLocationsFinder

import scala.beans.BooleanBeanProperty

class ScalaTestingFrameworkEntryPoint extends EntryPoint {

  @BooleanBeanProperty
  var selected: Boolean = true

  override def getDisplayName: String = TestingSupportBundle.message("scala.test.entry.points")

  override def isEntryPoint(refElement: RefElement, psiElement: PsiElement): Boolean =
    isEntryPoint(psiElement)

  override def isEntryPoint(psiElement: PsiElement): Boolean = {

    def isTestClass(clazz: ScTemplateDefinition): Boolean = {
      val framework = TestFrameworks.detectFramework(clazz)
      framework != null && framework.isTestClass(clazz)
    }

    def isScalaTestRefSpecMethodOrObject(element: PsiElement): Boolean =
      Option(PsiTreeUtil.getTopmostParentOfType(element, classOf[ScClass])).exists { definition =>
        ScalaTestTestLocationsFinder.calculateTestLocations(definition).contains(element)
      }

    psiElement match {
      case templateDefinition: ScTemplateDefinition if isTestClass(templateDefinition) => true

      case methodOrObject@(_: ScFunctionDefinition | _: ScObject) =>
        Option(PsiTreeUtil.getParentOfType(psiElement, classOf[ScClass]))
          .flatMap(scClass => Option(TestFrameworks.detectFramework(scClass)))
          .exists { _ => isScalaTestRefSpecMethodOrObject(methodOrObject) }

      case _ => false
    }
  }

  override def readExternal(element: Element): Unit =
    XmlSerializer.deserializeInto(this, element)

  override def writeExternal(element: Element): Unit =
    XmlSerializer.serializeInto(this, element)

}
