package org.jetbrains.plugins.scala
package lang
package lexer

import java.lang

import com.intellij.lexer.{FlexAdapter, MergingLexerAdapter}
import com.intellij.psi.tree.{IElementType, TokenSet}
import org.jetbrains.plugins.scala.lang.lexer.ScalaPlainLexer.ScalaSplittingLayerLexer

// NB Standard LayeredLexer is not apt for the task (because it doesn't propagate state in layers
// (and here we have main Scala lexer as a layer, so incremental highlighting would be completely broken).
final class ScalaPlainLexer(isScala3: Boolean,
                            treatDocCommentAsBlockComment: Boolean)
  extends LayeredLexer(ScalaSplittingLayerLexer(treatDocCommentAsBlockComment)) {

  import ScalaPlainLexer._
  {
    val isDisabled = IsDisabled()
    IsDisabled() = false

    registerSelfStoppingLayer(
      ScalaLayerLexer(isScala3),
      Array(ScalaTokenTypesEx.SCALA_PLAIN_CONTENT),
      IElementType.EMPTY_ARRAY
    )

    IsDisabled() = isDisabled
  }
}

object ScalaPlainLexer {

  import ScalaTokenTypes._
  import core.{_ScalaCoreLexer => ScalaCoreLexer, _ScalaSplittingLexer => ScalaSplittingLexer}
  import scaladoc.parser.ScalaDocElementTypes.SCALA_DOC_COMMENT

  private object IsDisabled {

    import LayeredLexer.ourDisableLayersFlag

    def apply(): lang.Boolean = ourDisableLayersFlag match {
      case null => null
      case flag => flag.get
    }

    def update(value: lang.Boolean): Unit = ourDisableLayersFlag match {
      case null =>
      case flag => flag.set(value)
    }
  }

  private object ScalaLayerLexer {

    private val TokensToMerge = TokenSet.create(
      tWHITE_SPACE_IN_LINE,
      tINTERPOLATED_MULTILINE_STRING,
      tINTERPOLATED_STRING,
      tINTERPOLATED_STRING_INJECTION
    )

    def apply(isScala3: Boolean) = new MergingLexerAdapter(
      new ScalaFlexLexer(isScala3),
      TokensToMerge
    )
  }

  private object ScalaSplittingLayerLexer {

    private val TokensToMerge = TokenSet.create(
      SCALA_DOC_COMMENT,
      tBLOCK_COMMENT,
      ScalaTokenTypesEx.SCALA_PLAIN_CONTENT
    )

    def apply(treatDocCommentAsBlockComment: Boolean) = new MergingLexerAdapter(
      new ScalaSplittingFlexLexer(treatDocCommentAsBlockComment),
      TokensToMerge
    )
  }

  private[this] final class ScalaFlexLexer(isScala3: Boolean)
    extends FlexAdapter(new ScalaCoreLexer(isScala3)) {

    override def getFlex: ScalaCoreLexer = super.getFlex.asInstanceOf[ScalaCoreLexer]

    override def getState: Int =
      super.getState << 1 | (if (getFlex.isInterpolatedStringState) 1 else 0)
  }

  private[this] final class ScalaSplittingFlexLexer(treatDocCommentAsBlockComment: Boolean)
    extends FlexAdapter(new ScalaSplittingLexer(null)) {

    override def getTokenType: IElementType = super.getTokenType match {
      case SCALA_DOC_COMMENT if treatDocCommentAsBlockComment => tBLOCK_COMMENT
      case elementType => elementType
    }
  }
}
