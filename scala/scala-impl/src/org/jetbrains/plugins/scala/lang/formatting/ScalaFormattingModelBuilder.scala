package org.jetbrains.plugins.scala
package lang
package formatting

import com.intellij.formatting._
import com.intellij.lang._
import com.intellij.openapi.util._
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.{FormattingDocumentModelImpl, PsiBasedFormattingModel}
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.formatting.ScalaFormattingModelBuilder._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.formatting.processors.ScalaFmtPreFormatProcessor
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

sealed class ScalaFormattingModelBuilder extends FormattingModelBuilder {

  override def createModel(element: PsiElement, settings: CodeStyleSettings): FormattingModel = {
    val node: ASTNode = element.getNode
    assert(node != null)
    val containingFile: PsiFile = element.getContainingFile.getViewProvider.getPsi(ScalaLanguage.INSTANCE)
    assert(containingFile != null, element.getContainingFile)
    val astNode: ASTNode = containingFile.getNode
    assert(astNode != null)
    val block: ScalaBlock = new ScalaBlock(null, astNode, null, null, Indent.getAbsoluteNoneIndent, null, settings)
    if (settings.getCustomSettings(classOf[ScalaCodeStyleSettings]).USE_SCALAFMT_FORMATTER) {
      //preprocessing is done by this point, use this little side-effect to clean-up rnages synchronization
      ScalaFmtPreFormatProcessor.clearRangesCache()
    }
    new ScalaFormattingModel(containingFile, block, FormattingDocumentModelImpl.createOn(containingFile))
  }

  override def getRangeAffectingIndent(file: PsiFile, offset: Int, elementAtOffset: ASTNode): TextRange = {
    elementAtOffset.getTextRange
  }
}

object ScalaFormattingModelBuilder {
 private class ScalaFormattingModel(file: PsiFile, rootBlock: Block, documentModel: FormattingDocumentModelImpl)
          extends PsiBasedFormattingModel(file, rootBlock, documentModel) {
    protected override def replaceWithPsiInLeaf(textRange: TextRange, whiteSpace: String, leafElement: ASTNode): String = {
      if (!myCanModifyAllWhiteSpaces) {
        if (ScalaTokenTypes.WHITES_SPACES_FOR_FORMATTER_TOKEN_SET.contains(leafElement.getElementType)) return null
      }
      var elementTypeToUse: IElementType = TokenType.WHITE_SPACE
      val prevNode: ASTNode = TreeUtil.prevLeaf(leafElement)
      if (prevNode != null && ScalaTokenTypes.WHITES_SPACES_FOR_FORMATTER_TOKEN_SET.contains(prevNode.getElementType)) {
        elementTypeToUse = prevNode.getElementType
      }
      inWriteAction(com.intellij.psi.formatter.FormatterUtil.replaceWhiteSpace(whiteSpace, leafElement, elementTypeToUse, textRange))
      whiteSpace
    }
  }
}