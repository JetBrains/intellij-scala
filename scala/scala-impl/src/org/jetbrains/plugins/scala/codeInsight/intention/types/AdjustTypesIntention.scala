package org.jetbrains.plugins.scala.codeInsight.intention.types

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, TypeAdjuster}

class AdjustTypesIntention extends PsiElementBaseIntentionAction {
  override def getFamilyName: String = ScalaBundle.message("family.name.adjust.types")

  override def getText: String = getFamilyName

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    findMaxReference(element).isDefined

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    findMaxReference(element) match {
      case Some(maxReference) =>
        //Example: in code `val x: a.b.c = ???` the same range includes both reference element and type element (parent of the ref)
        //We need to adjust types for the type element, not the ref element
        val elementsWithSameRange = ScalaPsiUtil.getElementsRange(maxReference, maxReference)
        TypeAdjuster.adjustFor(elementsWithSameRange)
      case _ =>
    }
  }

  private def findMaxReference(element: PsiElement): Option[PsiElement] = {
    val leafOrRef = element.withParentsInFile
      .takeWhile(_.is[LeafPsiElement, ScReference])
      .lastOption
    val ref = leafOrRef.filter(_.is[ScReference])
    val result = ref.filter(ref => ref.parentOfType[ScImportExpr].isEmpty)
    result
  }
}
