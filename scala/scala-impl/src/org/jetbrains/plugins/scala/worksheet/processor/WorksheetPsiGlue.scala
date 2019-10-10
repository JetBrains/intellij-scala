package org.jetbrains.plugins.scala.worksheet.processor

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetIncrementalEditorPrinter.{ClassObjectPsi, QueuedPsi, SingleQueuedPsi}

import scala.collection.mutable

class WorksheetPsiGlue(store: mutable.ListBuffer[QueuedPsi]) {

  def processPsi(psi: PsiElement): Unit = {
    def getMidText(first: PsiElement, second: PsiElement): String = {
      val builder = StringBuilder.newBuilder

      val it = second.prevSiblings
      while (it.hasNext) {
        val n = it.next()
        if (n == first) {
          return builder.toString()
        } else {
          builder.append(n.getText)
        }
      }
      
      builder.toString()
    }
    
    def processInner(clazz: ScClass, obj: ScObject, isClassFirst: Boolean) {
      val (f, s) = if (isClassFirst) (clazz, obj) else (obj, clazz)
      
      if (clazz.baseCompanionModule.contains(obj)) {
        store.remove(store.length - 1)
        store += ClassObjectPsi(clazz, obj, getMidText(f, s), isClassFirst)
        return
      }
      
      add(s)
    }
    
    
    (psi, store.lastOption) match {
      case (clazz: ScClass, Some(SingleQueuedPsi(obj: ScObject))) =>
        processInner(clazz, obj, isClassFirst = false)
      case (obj: ScObject, Some(SingleQueuedPsi(clazz: ScClass))) =>
        processInner(clazz, obj, isClassFirst = true)
      case (other, _) => add(other)
    }

  }

  private def add(psiElement: PsiElement) {
    store += SingleQueuedPsi(psiElement)
  }
}
