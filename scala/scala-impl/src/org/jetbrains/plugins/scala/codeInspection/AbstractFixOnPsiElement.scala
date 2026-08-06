package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.annotations.{Nls, NotNull}
import org.jetbrains.plugins.scala.extensions.ValidSmartPointer

/**
 * The purpose of this class is to avoid holding references to instances of PsiElement in a quickfix.
 * Quickfix may be invoked after reparsing of the file so psiElements which existed when quickfix was created may
 * be invalidated.
 */
abstract class AbstractFixOnPsiElement[T <: PsiElement](@Nls name: String, element: T)
  extends LocalQuickFixOnPsiElement(element) {

  override def getText: String = name

  override def getFamilyName: String = getText

  override final def invoke(
    @NotNull project: Project,
    @NotNull file: PsiFile,
    @NotNull startElement: PsiElement,
    @NotNull endElement: PsiElement
  ): Unit = {
    if (!IntentionPreviewUtils.prepareElementForWrite(file))
      return

    if (!startElement.isValid)
      return

    doApplyFix(startElement.asInstanceOf[T])(project)
  }

  protected def doApplyFix(element: T)
                          (implicit project: Project): Unit
}

abstract class AbstractFixOnTwoPsiElements[T <: PsiElement, S <: PsiElement](@Nls name: String,
                                                                             startElement: T,
                                                                             endElement: S)
  extends LocalQuickFixOnPsiElement(startElement, endElement) {

  override def getText: String = name

  override def getFamilyName: String = getText

  override final def invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Unit = {
    if (!IntentionPreviewUtils.prepareElementForWrite(file)) return

    myStartElement match {
      case ValidSmartPointer(first: T @unchecked) =>
        myEndElement match {
          // myEndElement is null when start and end elements are equal in LocalQuickFixOnPsiElement's constructor
          case null => doApplyFix(first, first.asInstanceOf[S])(project)
          case ValidSmartPointer(second: S @unchecked) => doApplyFix(first, second)(project)
          case _ =>
        }
      case _ =>
    }
  }

  protected def doApplyFix(first: T, second: S)
                          (implicit project: Project): Unit
}
