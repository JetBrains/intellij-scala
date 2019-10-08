package org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.folding

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase
import com.intellij.codeInspection.i18n.folding.EditPropertyValueAction
import com.intellij.lang.properties.references.PropertyReference
import com.intellij.openapi.editor.{Editor, FoldRegion}
import com.intellij.psi._
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.ScalaI18nUtil

class ScalaI18nMessageGotoDeclarationHandler extends GotoDeclarationHandlerBase {

  override def getGotoDeclarationTarget(myElement: PsiElement, editor: Editor): PsiElement = {
    val element = myElement

    val region: FoldRegion = editor.getFoldingModel.getCollapsedRegionAtOffset(element.getTextRange.getStartOffset)
    if (region == null) return null

    val editableElement: PsiElement = EditPropertyValueAction.getEditableElement(region)
    if (editableElement.isInstanceOf[ScLiteral])
      return resolve(editableElement)

    editableElement match {
      case methodCall: ScMethodCall =>
        for (expression <- methodCall.args.exprsArray) {
          expression match {
            case literal: ScLiteral if ScalaI18nUtil.isI18nProperty(expression.getProject, literal) =>
              return resolve(expression)
            case _ =>
          }
        }
      case _ =>
    }
    null
  }

  @Nullable private def resolve(element: PsiElement): PsiElement = {
    if (element == null) return null
    val references: Array[PsiReference] = element.getReferences
    if (references.length != 0) {
      for (reference <- references) {
        if (reference.isInstanceOf[PropertyReference]) return reference.resolve()
      }
    }
    null
  }
}
