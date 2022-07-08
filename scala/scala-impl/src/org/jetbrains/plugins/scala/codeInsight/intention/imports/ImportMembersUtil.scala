package org.jetbrains.plugins.scala
package codeInsight.intention.imports

import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScMethodCall, ScPostfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.project.ProjectContext

import java.util
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

object ImportMembersUtil {
  def isPackagingName(element: PsiElement): Boolean = element match {
    case Parent(packaging: ScPackaging) => packaging.reference.contains(element)
    case _ => false
  }

  def isInImport(element: PsiElement): Boolean =
    ScalaPsiUtil.getParentImportExpression(element) != null

  def hasQualifier(ref: ScReference): Boolean = {
    ref match {
      case _ childOf (ScInfixExpr(_: ScReferenceExpression, `ref`, _)) => true
      case _ childOf (ScPostfixExpr(_: ScReferenceExpression, `ref`)) => true
      case ScReferenceExpression.withQualifier(_: ScReferenceExpression) => true
      case stCodeRef: ScStableCodeReference => stCodeRef.qualifier.isDefined
      case _ => false
    }
  }

  def resolvesToStablePath(ref: ScReference): Boolean = {
    if (ref == null) return false

    ref.resolve() match {
      case (member: PsiMember) && (named: PsiNamedElement) =>
        ScalaPsiUtil.hasStablePath(named) && (member.hasModifierProperty(PsiModifier.STATIC) || member.containingClass == null)
      case named: PsiNamedElement => ScalaPsiUtil.hasStablePath(named)
      case _ => false
    }
  }

  @tailrec
  def replaceWithName(oldRef: ScReference, name: String): ScReference = {
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
        oldRef.replace(createExpressionFromText(name)).asInstanceOf[ScReference]
      case _: ScStableCodeReference =>
        oldRef.replace(createReferenceFromText(name)).asInstanceOf[ScReference]
      case _ => null
    }
  }

  @tailrec
  def replaceAndBind(oldRef: ScReference, name: String, toBind: PsiNamedElement): Unit = {
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
          case _: ScStableCodeReference =>
            val replaced = oldRef.replace(createReferenceFromText(name))
            replaced.asInstanceOf[ScStableCodeReference].bindToElement(toBind)
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
    def unapply(qual: ScReference): Option[ScReference] = {
      qual.getParent match {
        case ref @ ScReferenceExpression.withQualifier(`qual`) => Some(ref)
        case ScInfixExpr(`qual`, op, _) => Some(op)
        case ScPostfixExpr(`qual`, op: ScReference) => Some(op)
        case stRef: ScStableCodeReference if stRef.qualifier.contains(qual) => Some(stRef)
        case _ => None
      }
    }
  }

  object isQualifierInImport {
    def unapply(qual: ScStableCodeReference): Option[ScImportExpr] =
      ScalaPsiUtil.parentImportExpression(qual)
  }
}
