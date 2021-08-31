package org.jetbrains.plugins.scala
package codeInsight.intention.types

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.TypeAdjuster
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr

class AdjustTypesIntention extends PsiElementBaseIntentionAction {
  override def getFamilyName: String = ScalaBundle.message("family.name.adjust.types")

  override def getText: String = getFamilyName

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    findMaxReference(element).isDefined

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    TypeAdjuster.adjustFor(findMaxReference(element).toSeq)
  }

  private def findMaxReference(element: PsiElement): Option[PsiElement] =
    element.withParentsInFile
      .takeWhile(_.is[LeafPsiElement, ScReference])
      .lastOption
      .filter(_.is[ScReference])
      .filter(ref => ref.parentOfType[ScImportExpr].isEmpty)
}
