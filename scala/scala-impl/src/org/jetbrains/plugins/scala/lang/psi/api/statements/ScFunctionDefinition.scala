package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScInfixPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, StaticTraitScFunctionWrapper}
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

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

  def returnUsages(withBooleanInfix: Boolean = false): Array[PsiElement] = body.fold(Array.empty[PsiElement])(exp => {
    (exp.depthFirst(!_.isInstanceOf[ScFunction]).filter(_.isInstanceOf[ScReturnStmt]) ++ exp.calculateReturns(withBooleanInfix)).
      filter(_.getContainingFile == getContainingFile).toArray.distinct
  })

  def canBeTailRecursive: Boolean = getParent match {
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
    annotations.exists(_.typeElement.getType()
      .map(_.canonicalText).exists(_ == "_root_.scala.annotation.tailrec"))

  def recursiveReferences: Seq[RecursiveReference] = {
    val resultExpressions = returnUsages(withBooleanInfix = true)

    @scala.annotation.tailrec
    def possiblyTailRecursiveCallFor(elem: PsiElement): PsiElement = elem.getParent match {
      case call: ScMethodCall => possiblyTailRecursiveCallFor(call)
      case call: ScGenericCall => possiblyTailRecursiveCallFor(call)
      case infix: ScInfixExpr if infix.operation == elem => possiblyTailRecursiveCallFor(infix)
      case ret: ScReturnStmt => ret
      case _ => elem
    }

    def expandIf(elem: PsiElement): Seq[PsiElement] = {
      elem match {
        case i: ScIfStmt if i.elseBranch.isEmpty =>
          i.thenBranch match {
            case Some(thenBranch) =>
              thenBranch.calculateReturns().flatMap(expandIf) :+ elem
            case _ => Seq(elem)
          }
        case _ => Seq(elem)
      }
    }

    def quickCheck(ref: ScReferenceElement): Boolean = {
      ref match {
        case _: ScStableCodeReferenceElement  =>
          ref.getParent match {
            case ChildOf(_: ScConstructor) =>
              this.isSecondaryConstructor && containingClass.name == ref.refName
            case cp: ScConstructorPattern if cp.ref == ref =>
              this.name == "unapply" || this.name == "unapplySeq"
            case inf: ScInfixPattern if inf.reference == ref =>
              this.name == "unapply" || this.name == "unapplySeq"
            case _ => false
          }
        case _: ScReferenceExpression =>
          if (this.name == "apply")
            if (this.containingClass.isInstanceOf[ScObject]) this.containingClass.name == ref.refName
            else true
          else this.name == ref.refName
        case _ => false
      }
    }

    body match {
      case Some(b) =>
        val possiblyRecursiveRefs = b.depthFirst().filterByType[ScReferenceElement].filter(quickCheck).toSeq
        val recursiveRefs = possiblyRecursiveRefs.filter(_.isReferenceTo(this))
        if (recursiveRefs.nonEmpty) {
          val expressions = resultExpressions.flatMap(expandIf)
          recursiveRefs.map(ref => RecursiveReference(ref, expressions.contains(possiblyTailRecursiveCallFor(ref))))
        }

        else Seq.empty
      case _ => Seq.empty
    }
  }

  def recursionType: RecursionType = recursiveReferences match {
    case Seq() => RecursionType.NoRecursion
    case seq if seq.forall(_.isTailCall) => RecursionType.TailRecursion
    case _ => RecursionType.OrdinaryRecursion
  }

  override def controlFlowScope: Option[ScalaPsiElement] = body

  def isSecondaryConstructor: Boolean = name == "this"

  @Cached(ModCount.getBlockModificationCount, this)
  def getStaticTraitFunctionWrapper(cClass: PsiClassWrapper): StaticTraitScFunctionWrapper = {
    new StaticTraitScFunctionWrapper(this, cClass)
  }
}

object ScFunctionDefinition {
  object withBody {
    def unapply(fun: ScFunctionDefinition): Option[ScExpression] = Option(fun).flatMap(_.body)
  }
}

case class RecursiveReference(element: ScReferenceElement, isTailCall: Boolean)

trait RecursionType

object RecursionType {
  case object NoRecursion extends RecursionType
  case object OrdinaryRecursion extends RecursionType
  case object TailRecursion extends RecursionType
}