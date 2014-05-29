package org.jetbrains.plugins.scala
package codeInsight.intention.imports

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.LocalSearchScope
import scala.collection.JavaConversions._
import org.jetbrains.plugins.scala.extensions._
import com.intellij.psi.util.PsiTreeUtil
import ImportMembersUtil._

/**
* Nikolay.Tropin
* 2014-03-17
*/
class ImportStableMemberIntention extends PsiElementBaseIntentionAction {
  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val refAtCaret = PsiTreeUtil.getParentOfType(element, classOf[ScReferenceElement])
    if (refAtCaret == null) return false
    setText(s"Import ${refAtCaret.refName}")
    checkReference(refAtCaret)
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val refAtCaret = PsiTreeUtil.getParentOfType(element, classOf[ScReferenceElement])
    if (refAtCaret == null || !checkReference(refAtCaret)) return
    refAtCaret.resolve() match {
      case named: PsiNamedElement =>
        val importHolder = ScalaImportTypeFix.getImportHolder(element, project)
        val usages = ReferencesSearch.search(named, new LocalSearchScope(importHolder)).findAll()
        sorted(usages, isQualifier = false).foreach {
          case usage: ScReferenceElement if checkReference(usage) => replaceAndBind(usage, named.name, named)
          case _ =>
        }
      case _ =>
    }
  }

  override def getFamilyName: String = ImportStableMemberIntention.familyName

  private def checkReference(ref: ScReferenceElement): Boolean = {
    !isInImport(ref) && resolvesToStablePath(ref) && hasQualifier(ref)
  }
}

object ImportStableMemberIntention {
  val familyName = "Import member with stable path"
}
