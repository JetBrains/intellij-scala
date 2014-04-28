package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import com.intellij.psi._
import com.intellij.util.containers.ConcurrentHashMap
import light.{PsiClassWrapper, StaticTraitScFunctionWrapper}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import api.base.ScReferenceElement
import org.jetbrains.plugins.scala.extensions.{Parent, &&, toRichIterator, ElementText}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:49:36
*/
trait ScFunctionDefinition extends ScFunction with ScControlFlowOwner {
  def body: Option[ScExpression]

  def hasAssign: Boolean

  def assignment: Option[PsiElement]

  def removeAssignment()

  def getReturnUsages: Array[PsiElement] = body map (exp => {
    (exp.depthFirst(!_.isInstanceOf[ScFunction]).filter(_.isInstanceOf[ScReturnStmt]) ++ exp.calculateReturns).
      filter(_.getContainingFile == getContainingFile).toArray.distinct
  }) getOrElse Array.empty[PsiElement]

  def canBeTailRecursive = getParent match {
    case (_: ScTemplateBody) && Parent(Parent(owner: ScTypeDefinition)) =>
      val ownerModifiers = owner.getModifierList
      val methodModifiers = getModifierList
      owner.isInstanceOf[ScObject] ||
        ownerModifiers.has(ScalaTokenTypes.kFINAL) ||
        methodModifiers.has(ScalaTokenTypes.kPRIVATE) ||
        methodModifiers.has(ScalaTokenTypes.kFINAL)
    case _ => true
  }

  def hasTailRecursionAnnotation: Boolean =
    annotations.exists(_.typeElement.getType(TypingContext.empty)
      .map(_.canonicalText).exists(_ == "_root_.scala.annotation.tailrec"))

  def recursiveReferences: Seq[RecursiveReference] = {
    val resultExpressions = getReturnUsages

    @scala.annotation.tailrec
    def possiblyTailRecursiveCallFor(elem: PsiElement): PsiElement = elem.getParent match {
      case call: ScMethodCall => possiblyTailRecursiveCallFor(call)
      case call: ScGenericCall => possiblyTailRecursiveCallFor(call)
      case ret: ScReturnStmt => ret
      case infix @ ScInfixExpr(ScExpression.Type(types.Boolean), ElementText(op), right @ ScExpression.Type(types.Boolean))
        if right == elem && (op == "&&" || op == "||") => possiblyTailRecursiveCallFor(infix)
      case _ => elem
    }

    def expandIf(elem: PsiElement): Seq[PsiElement] = {
      elem match {
        case i: ScIfStmt if i.elseBranch.isEmpty =>
          i.thenBranch match {
            case Some(then) =>
              then.calculateReturns.flatMap(expandIf) :+ elem
            case _ => Seq(elem)
          }
        case _ => Seq(elem)
      }
    }
    val expressions = resultExpressions.flatMap(expandIf)
    for {
      ref <- depthFirst.filterByType(classOf[ScReferenceElement]).toSeq if ref.isReferenceTo(this)
    } yield {
      RecursiveReference(ref, expressions.contains(possiblyTailRecursiveCallFor(ref)))
    }
  }

  def recursionType: RecursionType = recursiveReferences match {
    case Seq() => RecursionType.NoRecursion
    case seq if seq.forall(_.isTailCall) => RecursionType.TailRecursion
    case _ => RecursionType.OrdinaryRecursion
  }

  override def controlFlowScope: Option[ScalaPsiElement] = body

  def isSecondaryConstructor: Boolean = name == "this"

  private var staticTraitFunctionWrapper: ConcurrentHashMap[(PsiClassWrapper), (StaticTraitScFunctionWrapper, Long)] =
    new ConcurrentHashMap()

  def getStaticTraitFunctionWrapper(cClass: PsiClassWrapper): StaticTraitScFunctionWrapper = {
    val curModCount = getManager.getModificationTracker.getOutOfCodeBlockModificationCount
    val r = staticTraitFunctionWrapper.get(cClass)
    if (r != null && r._2 == curModCount) {
      return r._1
    }
    val res = new StaticTraitScFunctionWrapper(this, cClass)
    staticTraitFunctionWrapper.put(cClass, (res, curModCount))
    res
  }
}

case class RecursiveReference(element: ScReferenceElement, isTailCall: Boolean)

trait RecursionType

object RecursionType {
  case object NoRecursion extends RecursionType
  case object OrdinaryRecursion extends RecursionType
  case object TailRecursion extends RecursionType
}