package org.jetbrains.plugins.scala.worksheet.cell

import java.lang.ref.WeakReference

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType

/**
  * User: Dmitry.Naydanov
  */
class CellDescriptor(startElementRef: WeakReference[PsiElement], runType: WorksheetExternalRunType, val externalId: Option[String]) {
  def getElement: Option[PsiElement] = Option(startElementRef.get())

  def getStartOffset: Option[Int] = getElement.map(_.getTextRange.getEndOffset)
  
  def getEndOffset: Option[Int] = getElement.map {
    el => 
      CellManager.getInstance(el.getProject).getNextCell(this).flatMap(
        _.getElement.map(_.getTextRange.getStartOffset)
      ).getOrElse(el.getContainingFile.getTextLength)
  }
  
  def getCellText: String = {
    getElement match {
      case Some(element) if element.isValid =>
        val file = element.getContainingFile
        val next = CellManager.getInstance(element.getProject).getNextCell(this)
        file.getText.substring(
          element.getTextRange.getEndOffset,
          next.flatMap(_.getElement.map(_.getTextOffset)).getOrElse(file.getTextLength)
        )
      case _ => null
    }
  }

  def createRunAction: Option[AnAction] = runType.createRunCellAction(this)
}

object CellDescriptor {
  def apply(startElement: PsiElement, runType: WorksheetExternalRunType, externalId: Option[String] = None): CellDescriptor =
    new CellDescriptor(new WeakReference[PsiElement](startElement), runType: WorksheetExternalRunType, externalId)
}