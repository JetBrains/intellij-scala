package org.jetbrains.plugins.scala.codeInspection.entrypoint

import com.intellij.codeInsight.TestFrameworks
import com.intellij.codeInspection.reference.{EntryPoint, RefElement}
import com.intellij.openapi.util.DefaultJDOMExternalizer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiMethod}
import org.jdom.Element

import scala.annotation.nowarn

class ScalaTestingFrameworkEntryPoint extends EntryPoint {

  var AddToTestEntries: Boolean = true

  override def getDisplayName: String = "Scala testing frameworks entry point detection"

  override def showUI(): Boolean = false

  override def isEntryPoint(refElement: RefElement, psiElement: PsiElement): Boolean =
    isEntryPoint(psiElement)

  override def isEntryPoint(psiElement: PsiElement): Boolean = {
    psiElement match {
      case clazz: PsiClass => isTestClass(clazz)
      case method: PsiMethod => isTestMethod(method)
      case _ => false
    }
  }

  override def isSelected: Boolean = AddToTestEntries

  override def setSelected(selected: Boolean): Unit = AddToTestEntries = selected

  @nowarn("cat=deprecation")
  override def readExternal(element: Element): Unit =
    DefaultJDOMExternalizer.readExternal(this, element)

  @nowarn("cat=deprecation")
  override def writeExternal(element: Element): Unit =
    if (!AddToTestEntries) DefaultJDOMExternalizer.writeExternal(this, element)

  private def isTestClass(clazz: PsiClass): Boolean = {
    val framework = TestFrameworks.detectFramework(clazz)
    framework != null && framework.isTestClass(clazz)
  }

  private def isTestMethod(method: PsiMethod): Boolean = {
    Option(PsiTreeUtil.getParentOfType(method, classOf[PsiClass])) match {
      case None => false
      case Some(clazz) =>
        val framework = TestFrameworks.detectFramework(clazz)
        framework != null && framework.isTestMethod(method)
    }
  }
}