package org.jetbrains.plugins.scala
package codeInsight.intention.imports

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.codeInsight.intention.imports.ImportMembersUtil._
import org.jetbrains.plugins.scala.extensions.PsiReferenceEx.resolve
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

class ImportAllMembersIntention extends PsiElementBaseIntentionAction {
  override def getFamilyName: String = ScalaBundle.message("family.name.import.all.members")

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val qualAtCaret = PsiTreeUtil.getParentOfType(element, classOf[ScReference])
    if (qualAtCaret == null) return false
    setText(ScalaBundle.message("import.all.members.of.reference", qualAtCaret.refName))
    checkQualifier(qualAtCaret)
  }


  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {

    case class RefWithUsage(ref: ScReference, named: PsiNamedElement)

    val qualAtCaret = PsiTreeUtil.getParentOfType(element, classOf[ScReference])
    if (qualAtCaret == null || !checkQualifier(qualAtCaret)) return
    qualAtCaret.resolve() match {
      case named: PsiNamedElement =>
        val importHolder = ScImportsHolder(element)(project)
        val usages = ReferencesSearch.search(named, new LocalSearchScope(importHolder)).findAll()
        val pathWithWildcard = ScalaNamesUtil.qualifiedName(named).getOrElse(return) + "._"

        val sortedUsages = sorted(usages, isQualifier = true).collect {
          case isQualifierFor(ref@resolve(resolved: PsiNamedElement)) if !isInImport(ref) => RefWithUsage(ref, resolved)
        }

        importHolder.addImportForPath(pathWithWildcard)

        sortedUsages.foreach { element =>
          replaceAndBind(element.ref, element.ref.refName, element.named)
        }
      case _ =>
    }
  }

  private def checkQualifier(qual: ScReference): Boolean = qual match {
    case isQualifierInImport(impExpr: ScImportExpr) =>
      val text = impExpr.getText
      !qual.textMatches(text) && !text.endsWith("_")
    case isQualifierFor(ref) => !isInImport(ref) && resolvesToStablePath(ref)
    case _ => false
  }
}
