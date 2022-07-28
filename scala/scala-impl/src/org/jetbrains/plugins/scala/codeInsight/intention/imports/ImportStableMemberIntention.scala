package org.jetbrains.plugins.scala
package codeInsight.intention.imports

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.codeInsight.intention.imports.ImportMembersUtil._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

class ImportStableMemberIntention extends PsiElementBaseIntentionAction {
  override def getFamilyName: String = ScalaBundle.message("family.name.import.member.with.stable.path")

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val refAtCaret = PsiTreeUtil.getParentOfType(element, classOf[ScReference])
    if (refAtCaret == null) return false
    setText(ScalaBundle.message("import.stable.member", refAtCaret.refName))
    checkReference(refAtCaret)
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val refAtCaret = PsiTreeUtil.getParentOfType(element, classOf[ScReference])
    if (refAtCaret == null || !checkReference(refAtCaret)) return
    refAtCaret.resolve() match {
      case named: PsiNamedElement =>
        val importHolder = ScImportsHolder(element)(project)
        val usages = ReferencesSearch.search(named, new LocalSearchScope(importHolder)).findAll()
        sorted(usages, isQualifier = false).foreach {
          case usage: ScReference if checkReference(usage) => replaceAndBind(usage, named.name, named)
          case _ =>
        }
      case _ =>
    }
  }

  private def checkReference(ref: ScReference): Boolean = {
    !isPackagingName(ref) && !isInImport(ref) && resolvesToStablePath(ref) && hasQualifier(ref)
  }
}
