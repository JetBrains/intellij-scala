package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.formatting._
import com.intellij.lang._
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util._
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.formatter.{FormattingDocumentModelImpl, PsiBasedFormattingModel, FormatterUtil => PsiFormatterUtil}
import com.intellij.psi.impl.source.tree.TreeUtil
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

final class ScalaFormattingModelBuilder extends FormattingModelBuilder {

  import ScalaFormattingModelBuilder._

  override def createModel(formattingContext: FormattingContext): FormattingModel = {
    val element = formattingContext.getPsiElement
    val styleSettings = formattingContext.getCodeStyleSettings

    Log.assertTrue(element.getNode != null, "AST should not be null for: " + element)

    if (styleSettings.getCustomSettings(classOf[settings.ScalaCodeStyleSettings]).USE_SCALAFMT_FORMATTER) {
      //preprocessing is done by this point, use this little side-effect to clean-up ranges synchronization
      // NOTE: looks like (only looks) this is not required?
      // I replaced this line directly in ScalaFmtPreFormatProcessor.process with rangesDeltaCache.remove(psiFile)
      //ScalaFmtPreFormatProcessor.clearRangesCache()
    }

    val file = element.getContainingFile
    val viewProvider = file.getViewProvider
    val containingFile = viewProvider.getPsi(viewProvider.getBaseLanguage)
    Log.assertTrue(containingFile != null, containingFile)

    val fileNode = file.getNode
    Log.assertTrue(fileNode != null, "AST should not be null for: " + containingFile)

    new ScalaFormattingModel(
      containingFile,
      new ScalaBlock(fileNode, null, null, Indent.getAbsoluteNoneIndent, null, styleSettings)
    )
  }

  override def getRangeAffectingIndent(file: PsiFile,
                                       offset: Int,
                                       elementAtOffset: ASTNode): TextRange =
    elementAtOffset.getTextRange
}

object ScalaFormattingModelBuilder {

  private val Log = Logger.getInstance(getClass)

  private final class ScalaFormattingModel(file: PsiFile, rootBlock: ScalaBlock)
    extends PsiBasedFormattingModel(file, rootBlock, FormattingDocumentModelImpl.createOn(file)) {

    import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.WHITES_SPACES_FOR_FORMATTER_TOKEN_SET

    protected override def replaceWithPsiInLeaf(
      textRange: TextRange,
      whiteSpace: String,
      leafElement: ASTNode
    ): String = {
      val elementType = leafElement.getElementType
      if (!myCanModifyAllWhiteSpaces && WHITES_SPACES_FOR_FORMATTER_TOKEN_SET.contains(elementType))
        null
      else {
        val prevLeaf = Option(TreeUtil.prevLeaf(leafElement))
        val prevLeafElementType = prevLeaf.map(_.getElementType)
        val whiteSpaceToken = prevLeafElementType.filter(WHITES_SPACES_FOR_FORMATTER_TOKEN_SET.contains)

        val runnable: Runnable = () => whiteSpaceToken match {
          case Some(wsType) =>
            PsiFormatterUtil.replaceWhiteSpace(whiteSpace, leafElement, wsType, textRange)
          case _ =>
            leafElement.getElementType match {
              case ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING =>
                //In multiline interpolated string leading spaces together with margin char are represented
                //by tINTERPOLATED_MULTILINE_STRING so we need to use replaceInnerWhiteSpace.
                //See JavaDoc of replaceInnerWhiteSpace for details
                PsiFormatterUtil.replaceInnerWhiteSpace(whiteSpace, leafElement, textRange)
              case _ =>
                PsiFormatterUtil.replaceWhiteSpace(whiteSpace, leafElement, TokenType.WHITE_SPACE, textRange)
            }
        }
        CodeStyleManager.getInstance(file.getProject).performActionWithFormatterDisabled(runnable)

        whiteSpace
      }
    }
  }
}
