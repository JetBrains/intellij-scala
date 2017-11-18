package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile, SmartPointerManager}

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
abstract class AbstractFixOnPsiElement[T <: PsiElement](name: String, startElement: T, endElement: T)
  extends LocalQuickFixOnPsiElement(startElement, endElement) {

  def this(name: String, element: T) = this(name, element, element)

  override def getText: String = name

  override def getFamilyName: String = getText

  import QuickFixHelper.validStartElement

  override final def invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Unit = {
    if (!FileModificationService.getInstance.prepareFileForWrite(file)) return

    val element = validStartElement[T](getStartElement)
    if (element != null) doApplyFix(element)(project)
  }

  protected def doApplyFix(element: T)
                          (implicit project: Project): Unit
}

abstract class AbstractFixOnTwoPsiElements[T <: PsiElement, S <: PsiElement](name: String, first: T, second: S)
  extends LocalQuickFixOnPsiElement(first) {

  private val secondReference = SmartPointerManager.getInstance(second.getProject)
    .createSmartPsiElementPointer(second)

  override def getText: String = name

  override def getFamilyName: String = getText

  import QuickFixHelper._

  override final def invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Unit = {
    if (!FileModificationService.getInstance.prepareFileForWrite(file)) return

    val first = validStartElement[T](getStartElement)
    val second = validElement(secondReference.getElement)

    if (first != null && second != null) doApplyFix(first, second)(project)
  }

  protected def doApplyFix(first: T, second: S)
                          (implicit project: Project): Unit
}

object QuickFixHelper {

  private[codeInspection] def validElement[T <: PsiElement](element: T): T =
    if (element != null && element.isValid) element else null.asInstanceOf[T]

  private[codeInspection] def validStartElement[T <: PsiElement](element: PsiElement): T =
    try {
      validElement(element.asInstanceOf[T])
    } catch {
      case _: ClassCastException => null.asInstanceOf[T]
    }
}
