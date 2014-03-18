package org.jetbrains.plugins.scala
package codeInsight.intention.imports

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiPackage, PsiMember, PsiNamedElement, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScStableCodeReferenceElement, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.LocalSearchScope
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import scala.collection.JavaConversions._
import org.jetbrains.plugins.scala.extensions.{childOf, toPsiNamedElementExt}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.codeInsight.intention.expression.{ConvertFromInfixExpressionIntention, ConvertToInfixExpressionIntention}
import scala.annotation.tailrec
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt

/**
* Nikolay.Tropin
* 2014-03-17
*/
class ImportStableMemberIntention extends PsiElementBaseIntentionAction {
  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val parentRef = PsiTreeUtil.getParentOfType(element, classOf[ScReferenceElement])
    if (parentRef == null) return false
    setText(s"Import ${parentRef.refName}")
    checkReference(parentRef)
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val parentRef = PsiTreeUtil.getParentOfType(element, classOf[ScReferenceElement])
    if (parentRef == null || !checkReference(parentRef)) return
    parentRef match {
      case ref: ScReferenceElement =>
        ref.resolve() match {
          case named: PsiNamedElement =>
            val importHolder = ScalaImportTypeFix.getImportHolder(element, project)
            val usages = ReferencesSearch.search(named, new LocalSearchScope(importHolder)).findAll()
            usages.foreach {
              case stRef: ScStableCodeReferenceElement if inImport(stRef) =>
              case r: ScReferenceElement => replaceAndBind(r, named.name, named)
              case _ =>
            }
          case _ =>
        }
      case _ =>
    }
  }

  override def getFamilyName: String = ImportStableMemberIntention.familyName

  @tailrec
  private def replaceAndBind(oldRef: ScReferenceElement, name: String, toBind: PsiNamedElement) {
    oldRef match {
      case _ childOf (inf @ ScInfixExpr(qual: ScReferenceExpression, `oldRef`, _)) =>
        val call = ConvertFromInfixExpressionIntention.createEquivMethodCall(inf)
        val replacedCall = inf.replace(call).asInstanceOf[ScMethodCall]
        val ref = replacedCall.getInvokedExpr.asInstanceOf[ScReferenceExpression]
        replaceAndBind(ref, name, toBind)
      case _ childOf (postfix @ ScPostfixExpr(qual: ScReferenceExpression, `oldRef`)) =>
        val withDot = postfix.replace(ScalaPsiElementFactory.createExpressionFromText(s"${qual.getText}.$name", oldRef.getManager))
                .asInstanceOf[ScReferenceExpression]
        replaceAndBind(withDot, name, toBind)
      case expr: ScReferenceExpression =>
        val clazz = toBind match {
          case m: PsiMember => Option(m.getContainingClass)
          case _ => None
        }
        val replaced = oldRef.replace(ScalaPsiElementFactory.createExpressionFromText(name, oldRef.getManager))
        replaced.asInstanceOf[ScReferenceExpression].bindToElement(toBind, clazz)
      case stCodeRef: ScStableCodeReferenceElement =>
        val replaced = oldRef.replace(ScalaPsiElementFactory.createReferenceFromText(name, oldRef.getManager))
        replaced.asInstanceOf[ScStableCodeReferenceElement].bindToElement(toBind)
      case _ =>
    }
  }

  private def inImport(element: PsiElement): Boolean = PsiTreeUtil.getParentOfType(element, classOf[ScImportStmt]) != null

  private def checkReference(ref: ScReferenceElement): Boolean = {
    def checkResolved(element: PsiElement) = element match {
      case named: PsiNamedElement if ScalaPsiUtil.hasStablePath(named) => true
      case _ => false
    }

    ref match {
      case _ childOf (ScInfixExpr(qual: ScReferenceExpression, `ref`, _)) =>
        checkResolved(qual.resolve())
      case _ childOf (ScPostfixExpr(qual: ScReferenceExpression, `ref`)) =>
        checkResolved(qual.resolve())
      case ScReferenceExpression.qualifier(qualRef: ScReferenceExpression) =>
        checkResolved(qualRef.resolve())
      case stCodeRef: ScStableCodeReferenceElement => stCodeRef.qualifier.exists {
        case qual: ScStableCodeReferenceElement =>
          checkResolved(qual.resolve())
        case _ => false
      }
      case _ => false
    }
  }
}

object ImportStableMemberIntention {
  val familyName = "Import member with stable path"
}
