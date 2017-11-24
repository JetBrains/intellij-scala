package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.ValidSmartPointer

/**
  * Nikolay.Tropin
  * 2014-11-12
  */

/**
  * The purpose of this class is to avoid holding references to instances of PsiElement in a quickfix.
  * Quickfix may be invoked after reparsing of the file so psiElements which existed when quickfix was created may
  * be invalidated.
  *
  * Important: Use methods getElement, getStartElement, getEndElement to get psiElements passed via constructor
  * arguments.
  */
abstract class AbstractFixOnPsiElement[T <: PsiElement](name: String, element: T)
  extends LocalQuickFixOnPsiElement(element) {

  override def getText: String = name

  override def getFamilyName: String = getText

  override final def invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Unit = {
    if (!FileModificationService.getInstance.prepareFileForWrite(file)) return

    myStartElement match {
      case ValidSmartPointer(element: T@unchecked) => doApplyFix(element)(project)
      case _ =>
    }
  }

  protected def doApplyFix(element: T)
                          (implicit project: Project): Unit
}

abstract class AbstractFixOnTwoPsiElements[T <: PsiElement, S <: PsiElement](name: String,
                                                                             startElement: T,
                                                                             endElement: S)
  extends LocalQuickFixOnPsiElement(startElement, endElement) {

  override def getText: String = name

  override def getFamilyName: String = getText

  override final def invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Unit = {
    if (!FileModificationService.getInstance.prepareFileForWrite(file)) return

    (myStartElement, myEndElement) match {
      case (ValidSmartPointer(first: T@unchecked), ValidSmartPointer(second: S@unchecked)) => doApplyFix(first, second)(project)
      case _ =>
    }
  }

  protected def doApplyFix(first: T, second: S)
                          (implicit project: Project): Unit
}
