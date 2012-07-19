package org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.folding

import com.intellij.codeInsight.folding.impl.EditorFoldingInfo
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.CompositeFoldingBuilder
import com.intellij.lang.folding.FoldingBuilder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.util.Key
import com.intellij.psi._
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.ScalaI18nUtil
import com.intellij.lang.properties.references.PropertyReference

/**
 * @author Ksenia.Sautina
 * @since 7/17/12
 */

class ScalaI18nMessageGotoDeclarationHandler extends GotoDeclarationHandlerBase {
  private final val KEY: Key[FoldingBuilder] = CompositeFoldingBuilder.FOLDING_BUILDER

  def getGotoDeclarationTarget(myElement: PsiElement, editor: Editor): PsiElement = {
    var element = myElement
    var i: Int = 4 //some street magic from Konstantin Bulenkov
    var flag = true
    while (element != null && i > 0 && flag) {
      val node: ASTNode = element.getNode
      if (node != null && node.getUserData(KEY) != null) {
        flag = false
      }
      else {
        i -= 1
        element = element.getParent
      }
    }
    if (element.isInstanceOf[ScLiteral]) {
      return resolve(element)
    }
    if (element.isInstanceOf[ScMethodCall]) {
      val methodCall: ScMethodCall = element.asInstanceOf[ScMethodCall]
      var foldRegion: FoldRegion = null
      for (region <- editor.getFoldingModel.getAllFoldRegions) {
        val psiElement: PsiElement = EditorFoldingInfo.get(editor).getPsiElement(region)
        if (methodCall == psiElement) {
          foldRegion = region
        }
      }
      if (foldRegion == null || foldRegion.isExpanded) return null
      for (expression <- methodCall.args.exprsArray) {
        if (expression.isInstanceOf[ScLiteral] && ScalaI18nUtil.isI18nProperty(expression.getProject, expression.asInstanceOf[ScLiteral])) {
          return resolve(expression)
        }
      }
    }
    null
  }

  @Nullable private def resolve(element: PsiElement): PsiElement = {
    if (element == null) return null
    val references: Array[PsiReference] = element.getReferences
    if (references.length == 0) null else {
      for (reference <- references) {
        if (reference.isInstanceOf[PropertyReference])
          return reference.resolve()
      }
    }
    null
  }
}
