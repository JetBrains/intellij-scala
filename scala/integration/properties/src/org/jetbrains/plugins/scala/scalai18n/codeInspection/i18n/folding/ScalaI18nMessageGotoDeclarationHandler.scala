package org.jetbrains.plugins.scala
package scalai18n
package codeInspection
package i18n
package folding

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase
import com.intellij.codeInspection.i18n.folding.EditPropertyValueAction.getEditableElement
import com.intellij.lang.properties.references.PropertyReference
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall

final class ScalaI18nMessageGotoDeclarationHandler extends GotoDeclarationHandlerBase {

  override def getGotoDeclarationTarget(element: PsiElement, editor: Editor): PsiElement =
    editor.getFoldingModel
      .getCollapsedRegionAtOffset(element.getTextRange.getStartOffset) match {
      case null => null
      case region => resolveReferenceIn(getEditableElement(region)).orNull
    }

  private def resolveReferenceIn(element: PsiElement) = element match {
    case literal: ScLiteral => resolveFirstPropertyReference(literal)
    case methodCall: ScMethodCall =>
      methodCall.argumentExpressions.find {
        case literal: ScLiteral => ScalaI18nUtil.isI18nProperty(literal.getProject, literal)
        case _ => false
      }.flatMap {
        case literal: ScLiteral => resolveFirstPropertyReference(literal)
      }
    case _ => None
  }

  private def resolveFirstPropertyReference(literal: ScLiteral) =
    literal.getReferences
      .find(_.isInstanceOf[PropertyReference])
      .map(_.resolve())
}
