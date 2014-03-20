package org.jetbrains.plugins.scala
package codeInsight.intention.imports

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiNamedElement, PsiElement}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import ImportMembersUtil._
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.LocalSearchScope
import scala.collection.JavaConverters._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import extensions.PsiReferenceEx.resolve


/**
* Nikolay.Tropin
* 2014-03-19
*/
class ImportAllMembersIntention extends PsiElementBaseIntentionAction {
  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val qualAtCaret = PsiTreeUtil.getParentOfType(element, classOf[ScReferenceElement])
    if (qualAtCaret == null) return false
    setText(s"Import all members of ${qualAtCaret.refName}")
    checkQualifier(qualAtCaret)
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val qualAtCaret = PsiTreeUtil.getParentOfType(element, classOf[ScReferenceElement])
    if (qualAtCaret == null || !checkQualifier(qualAtCaret)) return
    qualAtCaret.resolve() match {
      case named: PsiNamedElement =>
        val importHolder = ScalaImportTypeFix.getImportHolder(element, project)
        val usages = ReferencesSearch.search(named, new LocalSearchScope(importHolder)).findAll()
        val pathWithWildcard = ScalaNamesUtil.qualifiedName(named).getOrElse(return) + "._"
        importHolder.addImportForPath(pathWithWildcard)
        sorted(usages, isQualifier = true).foreach {
          case isQualifierFor(ref @ resolve(resolved: PsiNamedElement)) if !isInImport(ref) =>
            replaceAndBind(ref, ref.refName, resolved)
          case _ =>
        }
      case _ =>
    }
  }

  override def getFamilyName: String = ImportAllMembersIntention.familyName

  private def checkQualifier(qual: ScReferenceElement): Boolean = qual match {
    case isQualifierInImport(impExpr: ScImportExpr) =>
      val text = impExpr.getText
      qual.getText != text && !text.endsWith("_")
    case isQualifierFor(ref) => !isInImport(ref) && resolvesToStablePath(ref)
    case _ => false
  }
}

object ImportAllMembersIntention {
  val familyName = "Import all members"
}
