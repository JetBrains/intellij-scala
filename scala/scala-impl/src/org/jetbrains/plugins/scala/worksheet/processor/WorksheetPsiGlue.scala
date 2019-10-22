package org.jetbrains.plugins.scala.worksheet.processor

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetIncrementalEditorPrinter.{ClassObjectPsi, QueuedPsi, SemicolonSeqPsi, SingleQueuedPsi}

import scala.collection.mutable

class WorksheetPsiGlue private(val store: mutable.Buffer[QueuedPsi]) {

  private var afterSemicolon = false

  def result: Seq[QueuedPsi] = store

  def processPsi(psi: PsiElement): Unit = psi match {
    case (_: LeafPsiElement) && ElementType(ScalaTokenTypes.tSEMICOLON) => afterSemicolon = true
    case _: PsiComment                                                  => afterSemicolon &&= !psi.textContains('\n')
    case _: PsiWhiteSpace                                               => afterSemicolon &&= !psi.textContains('\n')
    case element: ScalaPsiElement =>
      process(element)
      afterSemicolon = false
    case _ =>
      afterSemicolon = false
  }

  private def add(psiElement: PsiElement): Unit =
    store += SingleQueuedPsi(psiElement)

  private def process(element: ScalaPsiElement): Unit = {

    /** @param clazz class or trait */
    def processInner(clazz: ScTypeDefinition, obj: ScObject, isClassFirst: Boolean) {
      val (first, second) = if (isClassFirst) (clazz, obj) else (obj, clazz)

      if (clazz.baseCompanionModule.contains(obj)) {
        store.remove(store.length - 1)
        store += ClassObjectPsi(clazz, obj, getTextBetween(first, second), isClassFirst)
      } else {
        add(second)
      }
    }

    val previousElement = store.lastOption match {
      case Some(value) => value
      case None        =>
        add(element)
        return
    }

    if (afterSemicolon) {
      // NOTE: it is not optimal to collect semicolons-separated elements like this,
      // but this requires the least changes and we consider semicolons as a rare edge case
      store.remove(store.length - 1)
      val elementsPrev = elements(previousElement)
      val elementsNew = elementsPrev :+ element
      store += SemicolonSeqPsi(elementsNew)
    } else (element, previousElement) match {
      case (clazz: ScClass, SingleQueuedPsi(obj: ScObject)) => processInner(clazz, obj, isClassFirst = false)
      case (traid: ScTrait, SingleQueuedPsi(obj: ScObject)) => processInner(traid, obj, isClassFirst = false)
      case (obj: ScObject, SingleQueuedPsi(clazz: ScClass)) => processInner(clazz, obj, isClassFirst = true)
      case (obj: ScObject, SingleQueuedPsi(traid: ScTrait)) => processInner(traid, obj, isClassFirst = true)
      case (other, _)                                       => add(other)
    }
  }

  private def getTextBetween(first: PsiElement, second: PsiElement): String = {
    val it = second.prevSiblings
    it.takeWhile(_ != first).map(_.getText).mkString
  }

  private def elements(last: QueuedPsi): Seq[PsiElement] = last match {
    case SingleQueuedPsi(psi)          => psi :: Nil
    case SemicolonSeqPsi(elements)     => elements
    case co@ClassObjectPsi(_, _, _, _) => co.first :: co.second :: Nil
  }
}

object WorksheetPsiGlue {

  def apply(): WorksheetPsiGlue = new WorksheetPsiGlue(mutable.ListBuffer[QueuedPsi]())
}
