package org.jetbrains.plugins.scala.worksheet.processor

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.worksheet.ui.printers.repl.QueuedPsi
import org.jetbrains.plugins.scala.worksheet.ui.printers.repl.QueuedPsi._

import scala.collection.{immutable, mutable}

private final class WorksheetPsiGlue {

  private val result = mutable.ArrayBuffer[QueuedPsi]()
  private val currentBuffer = mutable.ArrayBuffer[ScalaPsiElement]()

  private var afterSemicolon = false
  private var sealedTypeDef: Option[ScTypeDefinition] = None

  def prepareEvaluatedElements(elements: Iterable[PsiElement]): immutable.Seq[QueuedPsi] = {
    elements.foreach(this.process)
    flushCurrentBuffer()
    ///println(result)
    result.toList
  }

  private def process(psi: PsiElement): Unit = psi match {
    case (_: LeafPsiElement) && ElementType(ScalaTokenTypes.tSEMICOLON) =>
      afterSemicolon = true
    case _: PsiComment | _: PsiWhiteSpace =>
      afterSemicolon &&= !psi.textContains('\n')
    case element: ScalaPsiElement =>
      process(element)
      afterSemicolon = false
    case _ =>
      afterSemicolon = false
  }

  private def process(element: ScalaPsiElement): Unit = {
    val startsNewChunk = canStartIndependentChunk(element)
    if (startsNewChunk) {
      flushCurrentBuffer()
      sealedTypeDef = None
    }
    currentBuffer += element

    element match {
      case typeDef: ScTypeDefinition if typeDef.isSealed && sealedTypeDef.isEmpty =>
        sealedTypeDef = Some(typeDef)
      case _ =>
    }
  }

  private def flushCurrentBuffer(): Unit = {
    currentBuffer.toList match {
      case Nil =>
      case elements =>
        result += buildChunk(elements)
    }
    currentBuffer.clear()
  }

  private def buildChunk(elements: List[PsiElement]): QueuedPsi=
    if (elements.forall(_.isInstanceOf[ScTypeDefinition]))
      RelatedTypeDefs(elements.map(_.asInstanceOf[ScTypeDefinition]))
    else
      QueuedPsiSeq(elements)

  private def canStartIndependentChunk(current: ScalaPsiElement): Boolean = {
    if (afterSemicolon) return false
    if (currentBuffer.isEmpty) return true

    val previous = currentBuffer.last
    canStartIndependentChunk(current, previous)
  }

  private def canStartIndependentChunk(current: ScalaPsiElement, previous: ScalaPsiElement): Boolean =
    (current, previous) match {
      case (currentDef: ScTypeDefinition, previousDef: ScTypeDefinition) => !shouldGoTogether(currentDef, previousDef)
      case _                                                             => true
    }

  private def shouldGoTogether(current: ScTypeDefinition, previous: ScTypeDefinition): Boolean = {
    areCompanions(current, previous) || isInSealedHierarchy(current)
  }

  private def isInSealedHierarchy(typeDef: ScTypeDefinition): Boolean =
    sealedTypeDef.exists(base => typeDef.supers.exists(_ eq base))

  private def areCompanions(def1: ScTypeDefinition, def2: ScTypeDefinition) =
    canBeCompanions(def1, def2) && def1.baseCompanion.contains(def2)

  private def canBeCompanions(def1: ScTypeDefinition, def2: ScTypeDefinition): Boolean = (def1, def2) match {
    case (_: ScClass | _: ScTrait, _: ScObject) => true
    case (_: ScObject, _: ScClass | _: ScTrait) => true
    case _                                      => false
  }
}
