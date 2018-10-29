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
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec
import scala.collection.JavaConverters._

/**
 * Nikolay.Tropin
 * 2014-03-20
 */
object ImportMembersUtil {
  def isInImport(element: PsiElement): Boolean = PsiTreeUtil.getParentOfType(element, classOf[ScImportExpr]) != null

  def hasQualifier(ref: ScReferenceElement): Boolean = {
    ref match {
      case _ childOf (ScInfixExpr(_: ScReferenceExpression, `ref`, _)) => true
      case _ childOf (ScPostfixExpr(_: ScReferenceExpression, `ref`)) => true
      case ScReferenceExpression.withQualifier(_: ScReferenceExpression) => true
      case stCodeRef: ScStableCodeReferenceElement => stCodeRef.qualifier.isDefined
      case _ => false
    }
  }

  def resolvesToStablePath(ref: ScReferenceElement): Boolean = {
    if (ref == null) return false

    ref.resolve() match {
      case (member: PsiMember) && (named: PsiNamedElement) =>
        ScalaPsiUtil.hasStablePath(named) && (member.hasModifierProperty(PsiModifier.STATIC) || member.containingClass == null)
      case named: PsiNamedElement => ScalaPsiUtil.hasStablePath(named)
      case _ => false
    }
  }

  @tailrec
  def replaceWithName(oldRef: ScReferenceElement, name: String): ScReferenceElement = {
    import oldRef.projectContext

    oldRef match {
      case _ childOf (inf @ ScInfixExpr(_: ScReferenceExpression, `oldRef`, _)) =>
        val call = createEquivMethodCall(inf)
        val replacedCall = inf.replace(call).asInstanceOf[ScMethodCall]
        val ref = replacedCall.getInvokedExpr.asInstanceOf[ScReferenceExpression]
        replaceWithName(ref, name)
      case _ childOf (postfix @ ScPostfixExpr(qual: ScReferenceExpression, `oldRef`)) =>
        val withDot = postfix.replace(createExpressionFromText(s"${qual.getText}.$name"))
                .asInstanceOf[ScReferenceExpression]
        replaceWithName(withDot, name)
      case _: ScReferenceExpression =>
        oldRef.replace(createExpressionFromText(name)).asInstanceOf[ScReferenceElement]
      case _: ScStableCodeReferenceElement =>
        oldRef.replace(createReferenceFromText(name)).asInstanceOf[ScReferenceElement]
      case _ => null
    }
  }

  @tailrec
  def replaceAndBind(oldRef: ScReferenceElement, name: String, toBind: PsiNamedElement) {
    toBind match {
      case fun: ScFunction if fun.isSynthetic =>
        fun.syntheticNavigationElement match {
          case named: PsiNamedElement => replaceAndBind(oldRef, named.name, named)
          case _ =>
        }
      case _ =>
        implicit val projectContext: ProjectContext = oldRef.projectContext
        oldRef match {
          case _ childOf (inf @ ScInfixExpr(_: ScReferenceExpression, `oldRef`, _)) =>
            val call = createEquivMethodCall(inf)
            val replacedCall = inf.replaceExpression(call, removeParenthesis = true).asInstanceOf[ScMethodCall]
            val ref = replacedCall.getInvokedExpr.asInstanceOf[ScReferenceExpression]
            replaceAndBind(ref, name, toBind)
          case _ childOf (postfix @ ScPostfixExpr(_: ScReferenceExpression, `oldRef`)) =>
            val refExpr = createEquivQualifiedReference(postfix)
            val withDot = postfix.replaceExpression(refExpr, removeParenthesis = true).asInstanceOf[ScReferenceExpression]
            replaceAndBind(withDot, name, toBind)
          case expr: ScReferenceExpression =>
            val clazz = toBind match {
              case m: PsiMember => Option(m.getContainingClass)
              case _ => None
            }
            val refExpr = createExpressionFromText(name)
            val replaced = expr.replaceExpression(refExpr, removeParenthesis = true)
            replaced.asInstanceOf[ScReferenceExpression].bindToElement(toBind, clazz)
          case _: ScStableCodeReferenceElement =>
            val replaced = oldRef.replace(createReferenceFromText(name))
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
    usages.asScala.toSeq.sortWith(lessThan)
  }

  object isQualifierFor {
    def unapply(qual: ScReferenceElement): Option[ScReferenceElement] = {
      qual.getParent match {
        case ref @ ScReferenceExpression.withQualifier(`qual`) => Some(ref)
        case ScInfixExpr(`qual`, op, _) => Some(op)
        case ScPostfixExpr(`qual`, op: ScReferenceElement) => Some(op)
        case stRef: ScStableCodeReferenceElement if stRef.qualifier.contains(qual) => Some(stRef)
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
