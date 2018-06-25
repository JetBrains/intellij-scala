package org.jetbrains.plugins.scala.worksheet.cell

import java.lang.ref.WeakReference

import com.intellij.psi.PsiElement

/**
  * User: Dmitry.Naydanov
  */
class CellDescriptor(startElementRef: WeakReference[PsiElement]) {
  def getElement: Option[PsiElement] = Option(startElementRef.get())
  
  def getCellText: String = {
    getElement match {
      case Some(element) if element.isValid =>
        val file = element.getContainingFile
        val next = CellManager.getInstance(element.getProject).getNextCell(this)
        file.getText.substring (
          element.getTextRange.getEndOffset, 
          next.flatMap(_.getElement.map(_.getTextOffset)).getOrElse(file.getTextLength)
        )
      case _ => null
    }
  }
}
 object CellDescriptor {
   def apply(startElement: PsiElement): CellDescriptor = new CellDescriptor(new WeakReference[PsiElement](startElement))
 }