package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile, SmartPointerManager}

/**
* Nikolay.Tropin
* 2014-11-12
*/
abstract class AbstractFixOnPsiElement[T <: PsiElement](name: String, startElement: T, endElement: T)
        extends LocalQuickFixOnPsiElement(startElement, endElement) {

  def this(name: String, element: T) = this(name, element, element)

  override def getText: String = name

  override def getFamilyName = getText

  def getElement: T = {
    try {
      val elem = getStartElement.asInstanceOf[T]
      if (elem.isValid) elem
      else null.asInstanceOf[T]
    } catch {
      case e: ClassCastException => null.asInstanceOf[T]
    }
  }

  override def invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Unit = {
    if (!FileModificationService.getInstance.prepareFileForWrite(file)) return
    if (getElement == null) return
    doApplyFix(project)
  }

  def doApplyFix(project: Project)
}

abstract class AbstractFixOnTwoPsiElements[T <: PsiElement, S <: PsiElement](name: String, first: T, second: S)
        extends LocalQuickFixOnPsiElement(first) {

  private val secondRef = SmartPointerManager.getInstance(second.getProject).createSmartPsiElementPointer(second)

  override def getText: String = name

  override def getFamilyName = getText

  def getFirstElement: T = {
    try {
      val elem = getStartElement.asInstanceOf[T]
      if (elem.isValid) elem
      else null.asInstanceOf[T]
    } catch {
      case e: ClassCastException => null.asInstanceOf[T]
    }
  }

  def getSecondElement: S = {
    val elem = secondRef.getElement
    if (elem != null && elem.isValid) elem
    else null.asInstanceOf[S]
  }

  override def invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Unit = {
    if (!FileModificationService.getInstance.prepareFileForWrite(file)) return
    if (getFirstElement == null || getSecondElement == null) return
    doApplyFix(project)
  }

  def doApplyFix(project: Project)
}