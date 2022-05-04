package org.jetbrains.plugins.scala.codeInspection.entrypoint

import com.intellij.codeInsight.TestFrameworks
import com.intellij.codeInspection.reference.{EntryPoint, RefElement}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiMethod}
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}

import scala.beans.BooleanBeanProperty

class ScalaTestingFrameworkEntryPoint extends EntryPoint {

  @BooleanBeanProperty
  var selected: Boolean = true

  override def getDisplayName: String = ScalaInspectionBundle.message("scala.test.entry.points")

  override def isEntryPoint(refElement: RefElement, psiElement: PsiElement): Boolean =
    isEntryPoint(psiElement)

  override def isEntryPoint(psiElement: PsiElement): Boolean = {

    def isTestClass(clazz: PsiClass): Boolean = {
      val framework = TestFrameworks.detectFramework(clazz)
      framework != null && framework.isTestClass(clazz)
    }

    psiElement match {
      case psiClass: PsiClass if isTestClass(psiClass) => true

      case methodOrObject@(_: PsiMethod | _: ScObject) =>
        val testFramework =
          Option(PsiTreeUtil.getParentOfType(psiElement, classOf[ScClass]))
            .flatMap(scClass => Option(TestFrameworks.detectFramework(scClass)))

        testFramework match {
          case Some(framework) => framework.isTestMethod(methodOrObject)
          case _ => false
        }

      case _ => false
    }
  }

  override def readExternal(element: Element): Unit =
    XmlSerializer.deserializeInto(this, element)

  override def writeExternal(element: Element): Unit =
    XmlSerializer.serializeInto(this, element)

}
