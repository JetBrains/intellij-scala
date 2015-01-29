package org.jetbrains.plugins.scala
package codeInsight.intention.imports

import java.util

import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScMethodCall, ScPostfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

import scala.annotation.tailrec
import scala.collection.JavaConversions._

/**
 * Nikolay.Tropin
 * 2014-03-20
 */
object ImportMembersUtil {
  def isInImport(element: PsiElement): Boolean = PsiTreeUtil.getParentOfType(element, classOf[ScImportExpr]) != null

  def hasQualifier(ref: ScReferenceElement) = {
    ref match {
      case _ childOf (ScInfixExpr(qual: ScReferenceExpression, `ref`, _)) => true
      case _ childOf (ScPostfixExpr(qual: ScReferenceExpression, `ref`)) => true
      case ScReferenceExpression.withQualifier(qualRef: ScReferenceExpression) => true
      case stCodeRef: ScStableCodeReferenceElement => stCodeRef.qualifier.isDefined
      case _ => false
    }
  }

  def resolvesToStablePath(ref: ScReferenceElement): Boolean = {
    if (ref == null) return false

    ref.resolve() match {
      case Both(member: PsiMember, named: PsiNamedElement) =>
        ScalaPsiUtil.hasStablePath(named) && (member.hasModifierProperty(PsiModifier.STATIC) || member.containingClass == null)
      case named: PsiNamedElement => ScalaPsiUtil.hasStablePath(named)
      case _ => false
    }
  }

  @tailrec
  def replaceWithName(oldRef: ScReferenceElement, name: String): ScReferenceElement = {
    oldRef match {
      case _ childOf (inf @ ScInfixExpr(qual: ScReferenceExpression, `oldRef`, _)) =>
        val call = ScalaPsiElementFactory.createEquivMethodCall(inf)
        val replacedCall = inf.replace(call).asInstanceOf[ScMethodCall]
        val ref = replacedCall.getInvokedExpr.asInstanceOf[ScReferenceExpression]
        replaceWithName(ref, name)
      case _ childOf (postfix @ ScPostfixExpr(qual: ScReferenceExpression, `oldRef`)) =>
        val withDot = postfix.replace(ScalaPsiElementFactory.createExpressionFromText(s"${qual.getText}.$name", oldRef.getManager))
                .asInstanceOf[ScReferenceExpression]
        replaceWithName(withDot, name)
      case expr: ScReferenceExpression =>
        oldRef.replace(ScalaPsiElementFactory.createExpressionFromText(name, oldRef.getManager)).asInstanceOf[ScReferenceElement]
      case stCodeRef: ScStableCodeReferenceElement =>
        oldRef.replace(ScalaPsiElementFactory.createReferenceFromText(name, oldRef.getManager)).asInstanceOf[ScReferenceElement]
      case _ => null
    }
  }

  @tailrec
  def replaceAndBind(oldRef: ScReferenceElement, name: String, toBind: PsiNamedElement) {
    toBind match {
      case fun: ScFunction if fun.isSynthetic =>
        fun.getSyntheticNavigationElement match {
          case Some(named: PsiNamedElement) =>
            replaceAndBind(oldRef, named.name, named)
          case _ => return
        }
      case _ =>
        oldRef match {
          case _ childOf (inf @ ScInfixExpr(qual: ScReferenceExpression, `oldRef`, _)) =>
            val call = ScalaPsiElementFactory.createEquivMethodCall(inf)
            val replacedCall = inf.replaceExpression(call, removeParenthesis = true).asInstanceOf[ScMethodCall]
            val ref = replacedCall.getInvokedExpr.asInstanceOf[ScReferenceExpression]
            replaceAndBind(ref, name, toBind)
          case _ childOf (postfix @ ScPostfixExpr(qual: ScReferenceExpression, `oldRef`)) =>
            val refExpr = ScalaPsiElementFactory.createEquivQualifiedReference(postfix)
            val withDot = postfix.replaceExpression(refExpr, removeParenthesis = true).asInstanceOf[ScReferenceExpression]
            replaceAndBind(withDot, name, toBind)
          case expr: ScReferenceExpression =>
            val clazz = toBind match {
              case m: PsiMember => Option(m.getContainingClass)
              case _ => None
            }
            val refExpr = ScalaPsiElementFactory.createExpressionFromText(name, oldRef.getManager)
            val replaced = expr.replaceExpression(refExpr, removeParenthesis = true)
            replaced.asInstanceOf[ScReferenceExpression].bindToElement(toBind, clazz)
          case stCodeRef: ScStableCodeReferenceElement =>
            val replaced = oldRef.replace(ScalaPsiElementFactory.createReferenceFromText(name, oldRef.getManager))
            replaced.asInstanceOf[ScStableCodeReferenceElement].bindToElement(toBind)
          case _ =>
        }
    }
  }

  def sorted(usages: util.Collection[PsiReference], isQualifier: Boolean): Seq[PsiReference] = {
    def actuallyReplaced(ref: PsiReference): PsiElement = {
      val trueRef = if (!isQualifier) ref else ref match {
        case isQualifierFor(r) => r
        case _ => ref
      }
      trueRef match {
        case _ childOf (inf @ ScInfixExpr(_ , `trueRef`, _)) => inf
        case _ childOf (postfix @ ScPostfixExpr(_, `trueRef`)) => postfix
        case _ => trueRef.getElement
      }
    }
    val lessThan: (PsiReference, PsiReference) => Boolean = { (ref1, ref2) =>
      PsiTreeUtil.isAncestor(actuallyReplaced(ref2), actuallyReplaced(ref1), true)
    }
    usages.toSeq.sortWith(lessThan)
  }

  object isQualifierFor {
    def unapply(qual: ScReferenceElement): Option[ScReferenceElement] = {
      qual.getParent match {
        case ref @ ScReferenceExpression.withQualifier(`qual`) => Some(ref)
        case ScInfixExpr(`qual`, op, _) => Some(op)
        case ScPostfixExpr(`qual`, op: ScReferenceElement) => Some(op)
        case stRef: ScStableCodeReferenceElement if stRef.qualifier == Some(qual) => Some(stRef)
        case _ => None
      }
    }
  }

  object isQualifierInImport {
    def unapply(qual: ScStableCodeReferenceElement): Option[ScImportExpr] = {
      PsiTreeUtil.getParentOfType(qual, classOf[ScImportExpr]).toOption
    }
  }
}
