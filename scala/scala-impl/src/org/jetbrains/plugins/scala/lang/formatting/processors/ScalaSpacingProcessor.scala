package org.jetbrains.plugins.scala.lang.formatting.processors

import com.intellij.formatting.Spacing
import com.intellij.lang.ASTNode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.impl.source.tree.{LeafPsiElement, PsiWhiteSpaceImpl}
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.TokenSets
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.{ScalaKeywordTokenType, ScalaTokenType, ScalaTokenTypes, ScalaTokenTypesEx, ScalaXmlTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.{ScCodeBlockElementType, ScalaElementType}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportSelectors, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScPackaging}
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.{isIdentifier, isKeyword}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScalaDocSyntaxElementType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import org.jetbrains.plugins.scala.util.MultilineStringUtil
import org.jetbrains.plugins.scalaDirective.lang.parser.ScalaDirectiveElementTypes

import scala.annotation.{nowarn, tailrec}

// TODO: setup SCoverage for scala plugin, run tests and see
//  which branches of formatter subsystem are potentially dead code or uncovered

// TODO: uncomment suppressing of ScalaDeprecation

//noinspection InstanceOf,ScalaDeprecation,ScalaUnnecessaryParentheses
object ScalaSpacingProcessor extends ScalaTokenTypes {

  import ScalaElementType._

  private val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.lang.formatting.processors.ScalaSpacingProcessor")

  // TODO: minimize getText usages
  @deprecated("do not access block text directly, this potentially can be a heavyweight operation, use AST nodes")
  private def getText(node: ASTNode, fileText: CharSequence): String = {
    fileText.substring(node.getTextRange)
  }

  private def nodeTextStartsWith(node: ASTNode, fileText: CharSequence, char: Char): Boolean = {
    val range = node.getTextRange
    val offset = range.getStartOffset
    offset < fileText.length && fileText.charAt(offset) == char
  }

  private def nodeTextContainsNewLine(node: ASTNode, fileText: CharSequence): Boolean = {
    val range = node.getTextRange
    var index = range.getStartOffset
    val end = range.getEndOffset.min(fileText.length())
    while (index < end) {
      if (fileText.charAt(index) == '\n')
        return true
      index += 1
    }
    false
  }

  private def spacesToPreventNewIds(left: ScalaBlock, right: ScalaBlock, fileText: CharSequence, textRange: TextRange): Integer = {
    if (ScalaXmlTokenTypes.XML_ELEMENTS.contains(left.getNode.getElementType) ||
      ScalaXmlTokenTypes.XML_ELEMENTS.contains(right.getNode.getElementType)) return 0
    @tailrec
    def dfsChildren(currentNode: ASTNode, getChildren: ASTNode => List[ASTNode]): ASTNode = {
      getChildren(currentNode) find (_.getTextLength > 0) match {
        case Some(next) => dfsChildren(next, getChildren)
        case None => currentNode
      }
    }
    val leftNode = dfsChildren(left.lastNode.nullSafe.getOrElse(left.getNode), _.getChildren(null).toList.reverse)
    val rightNode = dfsChildren(right.getNode, _.getChildren(null).toList)

    (leftNode.getTreeParent.getElementType, rightNode.getTreeParent.getElementType) match {
      case (ScalaElementType.InterpolatedString, _) => 0
      case (_, ScalaElementType.InterpolatedString) => 0
      case _ if textRange.contains(rightNode.getTextRange) && textRange.contains(leftNode.getTextRange) =>
        val left = fileText.substring(leftNode.getTextRange)
        val right = fileText.substring(rightNode.getTextRange)
        val concatString = left + right
        if (isIdentifier(concatString) || isKeyword(concatString)) 1
        else 0
      case _ => 0
    }
  }

  def getSpacing(@Nullable left0: ScalaBlock, right: ScalaBlock): Spacing = {
    val settings = right.settings.getCommonSettings(ScalaLanguage.INSTANCE)

    if (left0 == null) {
      val keepBlankLines = if (settings.KEEP_LINE_BREAKS) settings.KEEP_BLANK_LINES_IN_CODE else 0
      return Spacing.createSpacing(0, 0, 0, settings.KEEP_LINE_BREAKS, keepBlankLines)
    }

    val left = getPrevBlockForLineCommentInTheEndOfLine(left0)
    val leftIsLineComment = !(left eq left0) || {
      val node = if (left.lastNode == null) left.node else left.lastNode
      node != null && node.getElementType == ScalaTokenTypes.tLINE_COMMENT
    }

    val scalaSettings = right.settings.getCustomSettings(classOf[ScalaCodeStyleSettings])

    getSpacingImpl(left, right, leftIsLineComment, settings, scalaSettings)
  }

  /**
   * When left block is a line comment at the end of the line, we substitute previous block instead of the comment block.
   * Such line comment shouldn't affect anything during the formatting, so we kinda ignore them.
   *
   * For example, in this code: {{{
   * class A // comment
   * class B
   * }}}
   * the comment shouldn't play any role in how the spacing between classes is calculated
   */
  private def getPrevBlockForLineCommentInTheEndOfLine(left: ScalaBlock) =
    if (left.getNode.getElementType == ScalaTokenTypes.tLINE_COMMENT) {
      val prev = prevOnSameLine(left.getNode)
      if (prev == null) left
      else dummyBlock(left, prev)
    }
    else left

  // NOTE: align, indent, wrap don't matter in spacing processor
  private def dummyBlock(left: ScalaBlock, prev: ASTNode): ScalaBlock =
    new ScalaBlock(prev, null, null, null, null, left.settings, None)

  private def prevOnSameLine(node: ASTNode): ASTNode =
    node.getTreePrev match {
      case ws: PsiWhiteSpaceImpl =>
        if (ws.textContains('\n')) null
        else ws.getTreePrev
      case prev => prev
    }

  // extra method for easier debugging
  // (the method contains a lot of returns, and it's hard to just debug the result for specific input)
  @inline
  @nowarn("cat=deprecation")
  private def getSpacingImpl(
    left: ScalaBlock,
    right: ScalaBlock,
    leftIsLineComment: Boolean,
    settings: CommonCodeStyleSettings,
    scalaSettings: ScalaCodeStyleSettings
  ): Spacing = {

    // if keepLineBreaks is disabled but we have a line comment on a previous line,
    // we shouldn't remove the line break, because it can lead to a broken code, for example:
    // if (true) // comment
    // {
    // }
    // should NOT transform to
    // if (true) // comment {
    // }
    val keepLineBreaks: Boolean = settings.KEEP_LINE_BREAKS || leftIsLineComment

    val keepBlankLinesInCode        : Int = settings.KEEP_BLANK_LINES_IN_CODE
    val keepBlankLinesInDeclarations: Int = settings.KEEP_BLANK_LINES_IN_DECLARATIONS
    val keepBlankLinesBeforeRBrace  : Int = settings.KEEP_BLANK_LINES_BEFORE_RBRACE

    def getSpacing(minMaxSpaces: Int, minLineFeeds: Int): Spacing =
      Spacing.createSpacing(
        minMaxSpaces,
        minMaxSpaces,
        minLineFeeds,
        keepLineBreaks,
        if (keepLineBreaks) keepBlankLinesInCode else 0
      )

    def getDependentLFSpacing(minMaxSpaces: Int, range: TextRange): Spacing =
      Spacing.createDependentLFSpacing(
        minMaxSpaces,
        minMaxSpaces,
        range,
        keepLineBreaks,
        if (keepLineBreaks) keepBlankLinesInCode else 0
      )

    //new formatter spacing

    val leftNode        = left.getNode
    val rightNode       = right.getNode
    val leftNodeParent  = leftNode.getTreeParent
    val rightNodeParent = rightNode.getTreeParent

    val leftElementType            = leftNode.getElementType
    val rightElementType           = rightNode.getElementType
    val leftNodeParentElementType  = leftNodeParent.getElementType
    val rightNodeParentElementType = rightNodeParent.getElementType

    val leftPsi        = leftNode.getPsi
    val rightPsi       = rightNode.getPsi
    val leftPsiParent  = leftNode.getPsi.getParent
    val rightPsiParent = rightNode.getPsi.getParent

    // TODO: it's not good for performance to extract entire file text on each reformat
    //  (it can be reformat of tiny block, e.g. after tiny refactoring)
    val fileText = PsiDocumentManager.getInstance(leftPsi.getProject).nullSafe
      .map(_.getDocument(leftPsi.getContainingFile))
      .map(_.getImmutableCharSequence)
      .getOrElse(leftPsi.getContainingFile.charSequence)

    val fileTextRange = new TextRange(0, fileText.length())

    val leftTextRange  = left.getTextRange
    val rightTextRange = right.getTextRange

    /**
     * This is not nodes text! This is blocks text, which can be different from node.
     *
     * @todo consider not using substring for left and right: for big notes it's not cheep operation
     */
    @deprecated("do not access block text directly, this potentially can be a heavyweight operation, use AST nodes")
    val (leftBlockString, rightBlockString) =
      if (fileTextRange.contains(leftTextRange) && fileTextRange.contains(rightTextRange)) {
        (fileText.substring(leftTextRange), fileText.substring(rightTextRange))
      } else {
        LOG.error("File text: \n%s\n\nDoesn't contains nodes:\n(%s, %s)".format(fileText, leftPsi.getText, rightPsi.getText))
        (leftPsi.getText, rightPsi.getText)
      }

    val spacesMin: Integer = spacesToPreventNewIds(left, right, fileText, fileTextRange)
    val WITHOUT_SPACING = getSpacing(spacesMin, 0)
    val WITHOUT_SPACING_NO_KEEP = Spacing.createSpacing(spacesMin, spacesMin, 0, leftIsLineComment, 0)
    val WITHOUT_SPACING_DEPENDENT = (range: TextRange) => getDependentLFSpacing(spacesMin, range)
    val WITH_SPACING = getSpacing(1, 0)
    val WITH_SPACING_NO_KEEP = Spacing.createSpacing(1, 1, 0, leftIsLineComment, 0)
    val WITH_SPACING_DEPENDENT = (range: TextRange) => getDependentLFSpacing(1, range)
    val ON_NEW_LINE = getSpacing(0, 1)
    val DOUBLE_LINE = getSpacing(0, 2)

    val NO_SPACING_WITH_NEWLINE = Spacing.createSpacing(0, 0, 0, true, 1)
    val NO_SPACING = Spacing.createSpacing(spacesMin, spacesMin, 0, false, 0)
    val COMMON_SPACING = Spacing.createSpacing(1, 1, 0, keepLineBreaks, 100)
    val IMPORT_BETWEEN_SPACING = Spacing.createSpacing(0, 0, 1, true, 100)

    def withSpacingIf(condition: Boolean) = if (condition) WITH_SPACING else WITHOUT_SPACING

    if (rightPsi.isInstanceOf[PsiComment] && settings.KEEP_FIRST_COLUMN_COMMENT)
      return Spacing.createKeepingFirstColumnSpacing(0, Integer.MAX_VALUE, true, settings.KEEP_BLANK_LINES_IN_CODE)

    import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
    if (leftPsi.isInstanceOf[PsiComment] && rightPsi.isInstanceOf[PsiComment]) {
      return ON_NEW_LINE
    }

    if (leftElementType == ScalaDirectiveElementTypes.SCALA_DIRECTIVE || rightElementType == ScalaDirectiveElementTypes.SCALA_DIRECTIVE) {
      return ON_NEW_LINE
    }

    //Scala 3 quotation/splicing start tokens: '{ or ${
    leftElementType match {
      case ScalaTokenType.QuoteStart | ScalaTokenType.SpliceStart if rightElementType == tLBRACE =>
        return NO_SPACING
      case _ =>
    }
    leftNodeParentElementType match {
      case QUOTED_BLOCK | SPLICED_BLOCK_EXPR | SPLICED_PATTERN_EXPR =>
        val result = {
          //empty quoted block: '{ } should be formatted just as '{}
          if (leftElementType == tLBRACE && rightElementType == tRBRACE) NO_SPACING
          else COMMON_SPACING
        }
        return result
      case _ =>
    }

    val elementTypesWithParents  = (leftElementType, rightElementType, leftNodeParentElementType, rightNodeParentElementType)
    def processElementTypes(pf: PartialFunction[(IElementType, IElementType, IElementType, IElementType), Spacing]): Option[Spacing] =
      pf.lift(elementTypesWithParents)

    //ScalaDoc
    def docCommentOf(node: ASTNode) = node.getPsi.parentsInFile.findByType[ScDocComment]
      .getOrElse(throw new RuntimeException("Unable to find parent doc comment"))

    def isScalaDocListStart(typ: IElementType): Boolean =
      typ match {
        case ScalaDocTokenType.DOC_LIST_ITEM_HEAD |
             ScalaDocElementTypes.DOC_LIST_ITEM |
             ScalaDocElementTypes.DOC_LIST => true
        case _ => false
      }

    val tagSpacing =
      if (scalaSettings.SD_PRESERVE_SPACES_IN_TAGS)
        Spacing.createSpacing(0, Int.MaxValue, 0, false, 0)
      else WITH_SPACING

    val scaladocSpacing = elementTypesWithParents match {
      case (_, ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS, _, _) =>
        NO_SPACING_WITH_NEWLINE
      case (_, ScalaDocTokenType.DOC_COMMENT_END, _, _) =>
        //val version = docCommentOf(rightNode).version
        if (false /*version == 1*/)
          NO_SPACING_WITH_NEWLINE
        else
          WITH_SPACING
      case (ScalaDocTokenType.DOC_COMMENT_START, rightType, _, _) =>
        if (isScalaDocListStart(rightType)) Spacing.getReadOnlySpacing
        //else if (docCommentOf(leftNode).version == 1) NO_SPACING_WITH_NEWLINE
        else if (rightType == ScalaDocTokenType.DOC_WHITESPACE/*getText(rightNode, fileText)(0) == ' '*/) WITHOUT_SPACING
        else WITH_SPACING
      case (x, y, _, _) if !scalaSettings.ENABLE_SCALADOC_FORMATTING &&
        ScalaDocElementTypes.AllElementAndTokenTypes.contains(x) &&
        ScalaDocElementTypes.AllElementAndTokenTypes.contains(y) =>
        Spacing.getReadOnlySpacing
      case (ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS, rightType, _, _) =>
        rightType match {
          case t if isScalaDocListStart(t)      => Spacing.getReadOnlySpacing
          case ScalaDocTokenType.DOC_INNER_CODE => Spacing.getReadOnlySpacing
          case ScalaDocTokenType.DOC_WHITESPACE => WITHOUT_SPACING
          case _                                => WITH_SPACING
        }
      case (_, ScalaDocElementTypes.DOC_LIST_ITEM | ScalaDocElementTypes.DOC_LIST, _, _) =>
        Spacing.getReadOnlySpacing
      case (ScalaDocTokenType.DOC_LIST_ITEM_HEAD, _, _, _) if scalaSettings.SD_ALIGN_LIST_ITEM_CONTENT =>
        WITH_SPACING
      case (ScalaDocTokenType.DOC_TAG_NAME, _, _, _) =>
        if (nodeTextStartsWith(rightNode, fileText, ' ')) WITH_SPACING
        else tagSpacing
      case (ScalaDocTokenType.DOC_TAG_VALUE_TOKEN, _, ScalaDocElementTypes.DOC_TAG, _) =>
        tagSpacing
      case (
        ScalaDocTokenType.DOC_COMMENT_DATA,
        ScalaDocTokenType.DOC_COMMENT_DATA,
        ScalaDocElementTypes.DOC_PARAGRAPH,
        ScalaDocElementTypes.DOC_PARAGRAPH
     ) if leftNodeParentElementType eq rightNodeParentElementType=>
        //Handle case when there are two lines of same paragraph without leading asterisk.
        //Example:
        //   /**
        //    * hello
        //    world
        //    */
        //   class Example
        //This branch is primarily needed for range formatting inside scaladoc.
        //It can be invoked during some other actions like pasting to scaladoc
        return WITH_SPACING
      case (_, x, _, _) if ScalaDocTokenType.ALL_SCALADOC_TOKENS.contains(x) =>
        Spacing.getReadOnlySpacing
      case (x, TokenType.ERROR_ELEMENT, _, _) if ScalaDocTokenType.ALL_SCALADOC_TOKENS.contains(x) =>
        WITH_SPACING
      case (x, _, _, _) if ScalaDocTokenType.ALL_SCALADOC_TOKENS.contains(x) =>
        Spacing.getReadOnlySpacing
      case _ =>
        null
    }

    if (scaladocSpacing != null)
      return scaladocSpacing

    //Xml
    processElementTypes {
      case (ScalaElementType.XML_START_TAG, ScalaElementType.XML_END_TAG, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) Spacing.getReadOnlySpacing
        else WITHOUT_SPACING
      case (ScalaElementType.XML_START_TAG, ScalaXmlTokenTypes.XML_DATA_CHARACTERS, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) Spacing.getReadOnlySpacing
        else WITHOUT_SPACING
      case (ScalaXmlTokenTypes.XML_DATA_CHARACTERS, ScalaElementType.XML_END_TAG, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) Spacing.getReadOnlySpacing
        else WITHOUT_SPACING
      case (ScalaElementType.XML_START_TAG, _, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) Spacing.getReadOnlySpacing
        else ON_NEW_LINE
      case (_, ScalaElementType.XML_END_TAG, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) Spacing.getReadOnlySpacing
        else ON_NEW_LINE
      case (ScalaXmlTokenTypes.XML_DATA_CHARACTERS, ScalaXmlTokenTypes.XML_DATA_CHARACTERS, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) Spacing.getReadOnlySpacing
        else WITH_SPACING
      case (ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_TOKEN, ScalaXmlTokenTypes.XML_CHAR_ENTITY_REF, _, _) =>
        Spacing.getReadOnlySpacing
      case (ScalaXmlTokenTypes.XML_CHAR_ENTITY_REF, ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_TOKEN, _, _) =>
        Spacing.getReadOnlySpacing
      case (ScalaXmlTokenTypes.XML_DATA_CHARACTERS, ScalaXmlTokenTypes.XML_CDATA_END, _, _) =>
        Spacing.getReadOnlySpacing
      case (ScalaXmlTokenTypes.XML_DATA_CHARACTERS, _, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) Spacing.getReadOnlySpacing
        else ON_NEW_LINE
      case (ScalaXmlTokenTypes.XML_CDATA_START, ScalaXmlTokenTypes.XML_DATA_CHARACTERS, _, _) =>
        Spacing.getReadOnlySpacing
      case (_, ScalaXmlTokenTypes.XML_DATA_CHARACTERS, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) Spacing.getReadOnlySpacing
        else ON_NEW_LINE
      case (ScalaElementType.XML_EMPTY_TAG, ScalaElementType.XML_EMPTY_TAG, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) Spacing.getReadOnlySpacing
        else ON_NEW_LINE
      case (_, ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START | ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_END, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) Spacing.getReadOnlySpacing
        else NO_SPACING
      case (ScalaXmlTokenTypes.XML_START_TAG_START | ScalaXmlTokenTypes.XML_END_TAG_START |
            ScalaXmlTokenTypes.XML_CDATA_START | ScalaXmlTokenTypes.XML_PI_START, _, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) Spacing.getReadOnlySpacing
        else NO_SPACING
      case (_, ScalaXmlTokenTypes.XML_TAG_END | ScalaXmlTokenTypes.XML_EMPTY_ELEMENT_END |
               ScalaXmlTokenTypes.XML_CDATA_END | ScalaXmlTokenTypes.XML_PI_END, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) Spacing.getReadOnlySpacing
        else NO_SPACING
      case (ScalaXmlTokenTypes.XML_NAME, ScalaElementType.XML_ATTRIBUTE, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) Spacing.getReadOnlySpacing
        else COMMON_SPACING
      case (ScalaXmlTokenTypes.XML_NAME, ScalaXmlTokenTypes.XML_EQ, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) Spacing.getReadOnlySpacing
        else NO_SPACING
      case (ScalaXmlTokenTypes.XML_EQ, ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_START_DELIMITER |
                                       ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) Spacing.getReadOnlySpacing
        else NO_SPACING
      case (ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_START_DELIMITER, ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_TOKEN, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) Spacing.getReadOnlySpacing
        else NO_SPACING
      case (ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_TOKEN, ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_END_DELIMITER, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) Spacing.getReadOnlySpacing
        else NO_SPACING
      case (ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START | ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_END, _, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) Spacing.getReadOnlySpacing
        else NO_SPACING
      case (_, ScalaXmlTokenTypes.XML_DATA_CHARACTERS | ScalaXmlTokenTypes.XML_COMMENT_END |
               ScalaXmlTokenTypes.XML_COMMENT_CHARACTERS, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) Spacing.getReadOnlySpacing
        else NO_SPACING
      case (ScalaXmlTokenTypes.XML_DATA_CHARACTERS | ScalaXmlTokenTypes.XML_COMMENT_START |
            ScalaXmlTokenTypes.XML_COMMENT_CHARACTERS, _, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) Spacing.getReadOnlySpacing
        else NO_SPACING
      case (el1, el2, _, _) if scalaSettings.KEEP_XML_FORMATTING &&
        (ScalaXmlTokenTypes.XML_ELEMENTS.contains(el1) || ScalaXmlTokenTypes.XML_ELEMENTS.contains(el2)) => Spacing.getReadOnlySpacing
      case (ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_START_DELIMITER, _, _, _) => Spacing.getReadOnlySpacing
      case (_, ScalaXmlTokenTypes.XML_ATTRIBUTE_VALUE_END_DELIMITER, _, _) => Spacing.getReadOnlySpacing
    } match {
      case Some(result) =>
        return result
      case _ =>
    }

    def isParenthesis(psi: PsiElement): Boolean = psi.is[ScParenthesizedElement]

    if (leftElementType == tLPARENTHESIS && isParenthesis(leftPsiParent)) {
      return if (settings.PARENTHESES_EXPRESSION_LPAREN_WRAP)
        if (settings.SPACE_WITHIN_PARENTHESES) WITH_SPACING_DEPENDENT(leftPsiParent.getTextRange)
        else WITHOUT_SPACING_DEPENDENT(leftPsiParent.getTextRange)
      else if (settings.SPACE_WITHIN_PARENTHESES) WITH_SPACING
      else WITHOUT_SPACING
    }

    //multiline strings
    if (scalaSettings.MULTILINE_STRING_OPENING_QUOTES_ON_NEW_LINE && isMultiLineStringCase(rightPsi)) {
      return ON_NEW_LINE
    }

    leftPsi match {
      case l: ScStringLiteral if l.isMultiLineString && rightNode == leftNode =>
        return spacingForMultilineStringPart(l)
      case Parent(l: ScStringLiteral) if l.isMultiLineString =>
        return spacingForMultilineStringPart(l)
      case _ =>
    }

    def spacingForMultilineStringPart(l: ScLiteral): Spacing = {
      val marginChar = MultilineStringUtil.getMarginChar(leftPsi).toString
      if (MultilineStringUtil.looksLikeUsesMargins(l) && leftBlockString != marginChar && rightBlockString == marginChar) {
        NO_SPACING_WITH_NEWLINE
      } else if (rightBlockString == MultilineStringUtil.MultilineQuotes && scalaSettings.MULTILINE_STRING_ALIGN_DANGLING_CLOSING_QUOTES) {
        NO_SPACING_WITH_NEWLINE
      } else {
        Spacing.getReadOnlySpacing
      }
    }

    //for interpolated strings
    if (rightElementType == tINTERPOLATED_STRING_ESCAPE)
      return Spacing.getReadOnlySpacing
    if (rightElementType == tINTERPOLATED_STRING || rightElementType == tINTERPOLATED_MULTILINE_STRING) {
      return if (leftBlockString == MultilineStringUtil.getMarginChar(leftPsi).toString) Spacing.getReadOnlySpacing
      else WITHOUT_SPACING
    }
    if (leftElementType == ScalaElementType.INTERPOLATED_PREFIX_LITERAL_REFERENCE)
      return WITHOUT_SPACING
    if (rightElementType == tINTERPOLATED_STRING_END)
      return Spacing.getReadOnlySpacing
    if (leftElementType == tINTERPOLATED_STRING_INJECTION || rightElementType == tINTERPOLATED_STRING_INJECTION)
      return Spacing.getReadOnlySpacing
    if (Option(leftNode.getTreeParent.getTreePrev).exists(_.getElementType == tINTERPOLATED_STRING_ID))
      return Spacing.getReadOnlySpacing
    if (leftElementType == tWRONG_STRING || rightElementType == tWRONG_STRING)
      return Spacing.getReadOnlySpacing

    @tailrec
    def isMultiLineStringCase(psiElem: PsiElement): Boolean = {
      psiElem match {
        case ml: ScStringLiteral if ml.isMultiLineString =>
          val nodeOffset = rightNode.getTextRange.getStartOffset
          val magicCondition = rightTextRange.contains(new TextRange(nodeOffset, nodeOffset + 3))
          val actuallyMultiline = rightBlockString.contains("\n")
          magicCondition && actuallyMultiline

        case _: ScInfixExpr | _: ScReferenceExpression | _: ScMethodCall =>
          isMultiLineStringCase(psiElem.getFirstChild)
        case _ => false
      }
    }

    // detached line comment
    if (rightPsi.isInstanceOf[PsiComment] && rightElementType == ScalaTokenTypes.tLINE_COMMENT) {
      val result = if (scalaSettings.KEEP_COMMENTS_ON_SAME_LINE)
        COMMON_SPACING
      else {
        val isAloneOnTheLine = PsiTreeUtil.prevLeaf(rightPsi) match {
          case ws: PsiWhiteSpace => containsNewLine(fileText, ws.getTextRange)
          case _                 => false
        }
        if (isAloneOnTheLine) COMMON_SPACING
        else ON_NEW_LINE
      }
      return result
    }


    // ';' from right
    if (rightElementType == ScalaTokenTypes.tSEMICOLON) {
      val result = leftElementType match {
        case ScalaTokenTypes.tLBRACE => IMPORT_BETWEEN_SPACING
        case _ if leftIsLineComment  => WITHOUT_SPACING
        case _                       => NO_SPACING
      }
      return result
    }

    if (rightElementType == tRPARENTHESIS && isParenthesis(rightPsiParent)) {
      return if (settings.PARENTHESES_EXPRESSION_RPAREN_WRAP)
        if (settings.SPACE_WITHIN_PARENTHESES) WITH_SPACING_DEPENDENT(rightPsiParent.getTextRange)
        else WITHOUT_SPACING_DEPENDENT(rightPsiParent.getTextRange)
      else if (settings.SPACE_WITHIN_PARENTHESES) WITH_SPACING
      else WITHOUT_SPACING
    }
    if (leftElementType == tIDENTIFIER && rightPsi.isInstanceOf[ScArgumentExprList]) {
      if (!nodeTextStartsWith(rightNode, fileText, '{')) {
        return if (settings.SPACE_BEFORE_METHOD_CALL_PARENTHESES) WITH_SPACING else WITHOUT_SPACING
      }
    }

    if (leftElementType == tLPARENTHESIS && leftPsiParent.is[ScArgumentExprList, ScPatternArgumentList]) {
      val newLineAfterLParen =
        scalaSettings.CALL_PARAMETERS_NEW_LINE_AFTER_LPAREN == ScalaCodeStyleSettings.NEW_LINE_ALWAYS ||
          scalaSettings.CALL_PARAMETERS_NEW_LINE_AFTER_LPAREN == ScalaCodeStyleSettings.NEW_LINE_FOR_MULTIPLE_ARGUMENTS &&
            leftPsiParent.asInstanceOf[ScArguments].getArgsCount > 1
      return if (newLineAfterLParen) {
        if (settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES && rightElementType != tRPARENTHESIS ||
          settings.SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES && rightElementType == tRPARENTHESIS) {
          WITH_SPACING_DEPENDENT(leftPsiParent.getTextRange)
        } else {
          WITHOUT_SPACING_DEPENDENT(leftPsiParent.getTextRange)
        }
      } else if (settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES && rightElementType != tRPARENTHESIS ||
        settings.SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES && rightElementType == tRPARENTHESIS) {
        WITH_SPACING
      } else {
        WITHOUT_SPACING
      }
    }
    if (rightElementType == tRPARENTHESIS && rightPsiParent.is[ScArgumentExprList, ScPatternArgumentList]) {
      return if (settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE)
        if (settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES) WITH_SPACING_DEPENDENT(rightPsiParent.getTextRange)
        else WITHOUT_SPACING_DEPENDENT(rightPsiParent.getTextRange)
      else if (settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES) WITH_SPACING
      else WITHOUT_SPACING
    }
    if (leftElementType == tLPARENTHESIS && leftPsiParent.isInstanceOf[ScParameterClause]) {
      return if (settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE)
        if (settings.SPACE_WITHIN_METHOD_PARENTHESES) WITH_SPACING_DEPENDENT(leftPsiParent.getTextRange)
        else WITHOUT_SPACING_DEPENDENT(leftPsiParent.getTextRange)
      else if (settings.SPACE_WITHIN_METHOD_PARENTHESES) WITH_SPACING
      else WITHOUT_SPACING
    }
    if (rightElementType == tRPARENTHESIS && rightPsiParent.isInstanceOf[ScParameterClause]) {
      return if (settings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE)
        if (settings.SPACE_WITHIN_METHOD_PARENTHESES) WITH_SPACING_DEPENDENT(rightPsiParent.getTextRange)
        else WITHOUT_SPACING_DEPENDENT(rightPsiParent.getTextRange)
      else if (settings.SPACE_WITHIN_METHOD_PARENTHESES) WITH_SPACING
      else WITHOUT_SPACING
    }

    if (nodeTextStartsWith(rightNode, fileText, '{')) {
      //todo: spacing for early definitions
      val result =
        if (rightPsi.isInstanceOf[ScImportSelectors]) WITHOUT_SPACING
        else if (leftPsiParent.isInstanceOf[ScParenthesisedTypeElement]) WITHOUT_SPACING
        else if (rightPsi.is[ScExtendsBlock, ScEarlyDefinitions, ScTemplateBody]) {
          settings.CLASS_BRACE_STYLE match {
            case CommonCodeStyleSettings.END_OF_LINE =>
              if (settings.SPACE_BEFORE_CLASS_LBRACE) WITH_SPACING_NO_KEEP
              else WITHOUT_SPACING_NO_KEEP
            case CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED =>
              val extendsBlock = rightPsi match {
                case e: ScExtendsBlock => e
                case t: ScEarlyDefinitions => t.getParent
                case t: ScTemplateBody => t.getParent
              }
              val startElement = extendsBlock.getParent match {
                case b: ScTypeDefinition => b.nameId
                case _: ScTemplateDefinition => extendsBlock
                case b => b
              }
              val startOffset = startElement.getTextRange.getStartOffset
              val range = new TextRange(startOffset, rightPsi.getTextRange.getStartOffset)
              if (settings.SPACE_BEFORE_CLASS_LBRACE) WITH_SPACING_DEPENDENT(range)
              else WITHOUT_SPACING_DEPENDENT(range)
            case _ =>
              ON_NEW_LINE
          }
        } else rightPsiParent match {
          case _: ScBlock | _: ScEarlyDefinitions | _: ScTemplateBody | _: ScalaFile => ON_NEW_LINE
          case _: ScPackaging if rightPsi.is[ScBlock] =>
            //Example:
            //package aaa.bbb.ccc
            ////(notice bank like here)
            //{ some code in block}
            //It's quite a dummy code but still...
            WITH_SPACING
          case _: ScArgumentExprList if rightPsi.isInstanceOf[ScBlock] => WITH_SPACING //don't add/remove newlines for partial function arguments
          case parent =>
            val (needSpace, braceStyle, startElement) =
              parent match {
                case fun: ScFunction =>
                  (settings.SPACE_BEFORE_METHOD_LBRACE, settings.METHOD_BRACE_STYLE, fun.nameId)
                case _: ScMethodCall if rightPsi.isInstanceOf[ScArguments] =>
                  val style = settings.BRACE_STYLE
                  //Extra check is an optimization not to check `isInScala3File` all the time when default code style is used
                  //TODO (minor) we ask `isInScala3File` for every block, which is not optimal (it requires tree traversal to parent every time)
                  // ideally we need to store information `isScala3` somewhere in global context when constructing blocks for entire scala file
                  // (see other places using isInScala3File in formatter package)
                  val shouldEnforceBraceAtEndOfLine = style != CommonCodeStyleSettings.END_OF_LINE && left.node.getPsi.isInScala3File
                  val styleAdjusted = if (shouldEnforceBraceAtEndOfLine) CommonCodeStyleSettings.END_OF_LINE else style
                  (scalaSettings.SPACE_BEFORE_BRACE_METHOD_CALL, styleAdjusted, parent)
                case _ =>
                  (true, settings.BRACE_STYLE, parent)
              }

            braceStyle match {
              case CommonCodeStyleSettings.END_OF_LINE =>
                if (needSpace) WITH_SPACING_NO_KEEP
                else WITHOUT_SPACING_NO_KEEP
              case CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED =>
                val startOffset = startElement.getTextRange.getStartOffset
                val range = new TextRange(startOffset, rightPsi.getTextRange.getStartOffset)
                if (needSpace) WITH_SPACING_DEPENDENT(range)
                else WITHOUT_SPACING_DEPENDENT(range)
              case _ =>
                ON_NEW_LINE
            }
        }
      return result
    }

    //1. no spaces before braceless template body (with empty extends list)
    //   class A:
    //2. no spaces before braceless template body (with non-empty extends list)
    //   class A extends B:
    //   class A extends B with C:
    //3. add space between `:` and type in given-with (given structural instance)
    //   given intOrd: Ord42[Int] with ...
    rightPsi match {
      //NOTE: ScExtendsBlock with braces is handled before
      case eb: ScExtendsBlock =>
        val hasNonEmptyExtendsList = eb.firstChild.map(_.elementType).contains(ScalaTokenTypes.kEXTENDS)
        val isExtendsBlockInGivenInstance = leftElementType == ScalaTokenTypes.tCOLON
        val needsSpace = hasNonEmptyExtendsList || isExtendsBlockInGivenInstance
        return if (needsSpace) WITH_SPACING else WITHOUT_SPACING

      case tb: ScTemplateBody =>
        val needsSpace = tb.isEnclosedByBraces
        return if (needsSpace) WITH_SPACING else WITHOUT_SPACING
      case _ =>
    }

    //this is a dirty hack for SCL-9264. It looks bad, but seems to be the only fast way to make this work.
    (leftElementType, leftPsi.getPrevSiblingNotWhitespace) match {
      case (ScalaTokenTypes.tLBRACE | ScalaTokenTypes.tLPARENTHESIS, forNode: LeafPsiElement) if !left.isLeaf() &&
        forNode.getElementType == ScalaTokenTypes.kFOR => return COMMON_SPACING
      case _ =>
    }


    if (leftPsi.isInstanceOf[ScStableCodeReference] && !rightPsi.isInstanceOf[ScPackaging]) {
      leftPsiParent match {
        case p: ScPackaging if p.reference.contains(leftPsi) =>
          rightElementType match {
            // colon stand for Scala3 braceless package syntax `package p1.p2:\n  package p3`
            case ScalaTokenTypes.tLBRACE | ScalaTokenTypes.tCOLON =>
            case _ =>
              return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AFTER_PACKAGE + 1, keepLineBreaks, keepBlankLinesInCode)
          }
        case _ =>
      }
    }

    if (leftPsi.isInstanceOf[ScPackaging]) {
      return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AFTER_PACKAGE + 1, keepLineBreaks, keepBlankLinesInCode)
    }

    if (rightPsi.isInstanceOf[ScPackaging]) {
      val result =
        if (leftPsi.isInstanceOf[ScStableCodeReference] || leftElementType == tLBRACE)
          Spacing.createSpacing(0, 0, 1, keepLineBreaks, keepBlankLinesInCode)
        else if (leftPsi.isInstanceOf[PsiComment] && leftPsi.getPrevSiblingNotWhitespaceComment.isInstanceOf[ScStableCodeReference])
          ON_NEW_LINE
        else
          Spacing.createSpacing(0, 0, settings.BLANK_LINES_BEFORE_PACKAGE + 1, keepLineBreaks, keepBlankLinesInCode)
      return result
    }

    if (leftPsi.isInstanceOf[ScImportStmt] && !rightPsi.isInstanceOf[ScImportStmt]) {
      if (leftPsiParent.is[ScEarlyDefinitions, ScTemplateBody, ScalaFile, ScPackaging]) {
        return if (rightElementType == ScalaTokenTypes.tLINE_COMMENT) ON_NEW_LINE
        else Spacing.createSpacing(0, 0, settings.BLANK_LINES_AFTER_IMPORTS + 1, keepLineBreaks, keepBlankLinesInCode)
      }
      else return WITHOUT_SPACING
    }

    if (rightPsi.isInstanceOf[ScImportStmt] && !leftPsi.isInstanceOf[ScImportStmt]) {
      val leftIsImport = leftPsi.getPrevSiblingNotWhitespace.isInstanceOf[ScImportStmt]
      val leftIsCommentInsideImport = leftPsi.isInstanceOf[PsiComment] && leftPsi.getPrevSiblingNotWhitespaceComment.isInstanceOf[ScImportStmt]
      if (!leftIsImport && !leftIsCommentInsideImport) {
        if (rightPsiParent.is[ScEarlyDefinitions, ScTemplateBody, ScalaFile, ScPackaging]) {
          return Spacing.createSpacing(0, 0, settings.BLANK_LINES_BEFORE_IMPORTS + 1, keepLineBreaks, keepBlankLinesInCode)
        }
      }
    }

    if (leftPsi.isInstanceOf[ScImportStmt] || rightPsi.isInstanceOf[ScImportStmt]) {
      return Spacing.createSpacing(0, 0, 1, keepLineBreaks, keepBlankLinesInDeclarations)
    }

    // '}' or 'end'
    if (TokenSets.RBRACE_OR_END_STMT.contains(rightElementType)) {
      val rightTreeParent = rightNode.getTreeParent
      val result = rightTreeParent.getPsi match {
        case block@(_: ScEarlyDefinitions |
                    _: ScTemplateBody |
                    _: ScPackaging |
                    _: ScBlockExpr |
                    _: ScMatch |
                    _: ScCatchBlock) =>
          val checkKeepOneLineLambdas =
            scalaSettings.KEEP_ONE_LINE_LAMBDAS_IN_ARG_LIST && leftPsi.isInstanceOf[PsiComment]

          val checkOneLineSpaces: Boolean = {
            lazy val inMethod = scalaSettings.SPACES_IN_ONE_LINE_BLOCKS &&
              rightTreeParent.getTreeParent.nullSafe.exists(_.isInstanceOf[ScFunction])

            lazy val inSelfTypeBraces = scalaSettings.SPACE_INSIDE_SELF_TYPE_BRACES &&
              leftPsiParent.getFirstChild.getNextSiblingNotWhitespace.isInstanceOf[ScSelfTypeElement]

            lazy val inClosure = scalaSettings.SPACE_INSIDE_CLOSURE_BRACES && (leftElementType match {
              case ScalaElementType.FUNCTION_EXPR => true
              case ScalaElementType.CASE_CLAUSES => block.getParent.is[ScArgumentExprList, ScInfixExpr]
              case _ =>
                val insideInterpString = rightTreeParent.getTreeParent.nullSafe
                  .exists(_.getElementType == ScalaElementType.InterpolatedString)
                scalaSettings.KEEP_ONE_LINE_LAMBDAS_IN_ARG_LIST &&
                  (leftPsi.is[ScFunctionExpr, ScCaseClauses] || block.isInstanceOf[ScBlockExpr] && !insideInterpString)
            })
            val isOneLineEmpty = leftBlockString == "{" || nodeTextContainsNewLine(block.getNode, fileText)
            !isOneLineEmpty && (scalaSettings.SPACES_IN_ONE_LINE_BLOCKS || inMethod || inSelfTypeBraces || inClosure)
          }

          val needsSpace = checkKeepOneLineLambdas || checkOneLineSpaces
          val spaces = if (needsSpace) 1 else 0
          block match {
            case _: ScTemplateBody =>
              val isAnonymous = !PsiTreeUtil.getParentOfType(block, classOf[ScTemplateDefinition]).is[ScTypeDefinition]
              val skipMinLines =
                leftElementType == ScalaTokenTypes.tLBRACE ||
                  leftElementType == ScalaElementType.SELF_TYPE ||
                  COMMENTS_TOKEN_SET.contains(leftElementType)
              val minLineFeeds =
                if (skipMinLines)  0
                else if (isAnonymous) 1
                else settings.BLANK_LINES_BEFORE_CLASS_END + 1
              Spacing.createSpacing(spaces, spaces, minLineFeeds, keepLineBreaks, keepBlankLinesBeforeRBrace)
            case _ =>
              Spacing.createDependentLFSpacing(spaces, spaces, block.getTextRange, keepLineBreaks, keepBlankLinesBeforeRBrace)
          }
        case _: ScImportSelectors =>
          val refRange = leftNode.getTreeParent.getTextRange
          if (scalaSettings.SPACES_IN_IMPORTS) WITH_SPACING_DEPENDENT(refRange)
          else WITHOUT_SPACING_DEPENDENT(refRange)
        case _ =>
          Spacing.createSpacing(0, 0, 0, keepLineBreaks, keepBlankLinesBeforeRBrace)
      }
      return result
    }

    // TODO: do we need separate settings for : block syntax
    // '{' or ':'
    if (leftElementType == ScalaTokenTypes.tLBRACE || leftElementType == tCOLON && leftNodeParentElementType == TEMPLATE_BODY) {
      if (!scalaSettings.PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE) {
        val b = leftNode.getTreeParent.getPsi
        val spaceInsideOneLineBlock = scalaSettings.SPACES_IN_ONE_LINE_BLOCKS && !nodeTextContainsNewLine(b.getNode, fileText)
        val spacing = if (scalaSettings.SPACE_INSIDE_CLOSURE_BRACES || spaceInsideOneLineBlock) WITH_SPACING else WITHOUT_SPACING
        rightElementType match {
          case ScalaElementType.FUNCTION_EXPR => return spacing
          case ScalaElementType.CASE_CLAUSES =>
            if (b.getParent.isInstanceOf[ScArgumentExprList] || b.getParent.isInstanceOf[ScInfixExpr]) return spacing
          case _ =>
        }
      }

      return leftNode.getTreeParent.getPsi match {
        case _: ScTemplateBody if rightPsi.isInstanceOf[ScSelfTypeElement] =>
          if (scalaSettings.PLACE_SELF_TYPE_ON_NEW_LINE) ON_NEW_LINE
          else if (scalaSettings.SPACE_INSIDE_SELF_TYPE_BRACES) WITH_SPACING_NO_KEEP
          else WITHOUT_SPACING_NO_KEEP
        case b@(_: ScEarlyDefinitions | _: ScTemplateBody) =>
          if (settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE && !nodeTextContainsNewLine(b.getNode, fileText)) {
            Spacing.createDependentLFSpacing(0, 0, b.getTextRange, keepLineBreaks, keepBlankLinesBeforeRBrace)
          } else {
            val c = PsiTreeUtil.getParentOfType(b, classOf[ScTemplateDefinition])
            val minLineFeeds = if (c.isInstanceOf[ScTypeDefinition]) settings.BLANK_LINES_AFTER_CLASS_HEADER
            else settings.BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER
            Spacing.createSpacing(0, 0, minLineFeeds + 1, keepLineBreaks, keepBlankLinesInDeclarations)
          }
        case b: ScBlockExpr if b.getParent.isInstanceOf[ScFunction] =>
          if (settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE && !nodeTextContainsNewLine(b.getNode, fileText)) {
            val spaces = if (scalaSettings.SPACES_IN_ONE_LINE_BLOCKS) 1 else 0
            Spacing.createDependentLFSpacing(spaces, spaces, b.getTextRange, keepLineBreaks, keepBlankLinesBeforeRBrace)
          } else {
            Spacing.createSpacing(0, 0, settings.BLANK_LINES_BEFORE_METHOD_BODY + 1, keepLineBreaks, keepBlankLinesInDeclarations)
          }
        case b: ScBlockExpr if scalaSettings.KEEP_ONE_LINE_LAMBDAS_IN_ARG_LIST &&
          !nodeTextContainsNewLine(b.getNode, fileText) && (rightPsi.isInstanceOf[ScCaseClauses] && b.getParent != null &&
          b.getParent.isInstanceOf[ScArgumentExprList] || rightPsi.isInstanceOf[ScFunctionExpr]) =>
          Spacing.createDependentLFSpacing(1, 1, b.getTextRange, keepLineBreaks, keepBlankLinesBeforeRBrace)
        case b: ScBlockExpr if scalaSettings.SPACE_INSIDE_CLOSURE_BRACES && !nodeTextContainsNewLine(b.getNode, fileText) &&
          scalaSettings.KEEP_ONE_LINE_LAMBDAS_IN_ARG_LIST && b.getParent.is[ScArgumentExprList, ScInfixExpr] =>
          WITH_SPACING
        case block@(_: ScPackaging | _: ScBlockExpr | _: ScMatch | _: ScCatchBlock) =>
          val prev = block.getPrevSibling
          if (settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE || prev != null &&
            prev.getNode.getElementType == tINTERPOLATED_STRING_INJECTION) {
            val spaces = if (scalaSettings.SPACES_IN_ONE_LINE_BLOCKS) 1 else 0
            Spacing.createDependentLFSpacing(spaces, spaces, block.getTextRange, keepLineBreaks, keepBlankLinesBeforeRBrace)
          } else {
            ON_NEW_LINE
          }
        case _: ScImportSelectors =>
          val refRange = leftNode.getTreeParent.getTextRange
          if (scalaSettings.SPACES_IN_IMPORTS) WITH_SPACING_DEPENDENT(refRange)
          else WITHOUT_SPACING_DEPENDENT(refRange)
        case _ =>
          Spacing.createSpacing(0, 0, 0, keepLineBreaks, keepBlankLinesBeforeRBrace)
      }
    }

    if (leftPsi.isInstanceOf[ScSelfTypeElement]) {
      val c = PsiTreeUtil.getParentOfType(leftPsi, classOf[ScTemplateDefinition])
      val minLineFeeds = if (c.isInstanceOf[ScTypeDefinition]) settings.BLANK_LINES_AFTER_CLASS_HEADER
      else settings.BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER
      return Spacing.createSpacing(0, 0, minLineFeeds + 1, keepLineBreaks, keepBlankLinesInDeclarations)
    }

    // spacing between members in different scopes (class, trait(interface), local scope)
    if (
      leftPsi.is[ScTypeDefinition, ScFunction, ScValueOrVariable, ScTypeAlias, ScExpression] &&
        !rightPsi.is[PsiComment] ||
        rightPsi.is[ScTypeDefinition, ScFunction, ScValueOrVariable, ScTypeAlias]
    ) {
      val cs = settings
      val ss = scalaSettings

      import BlankLinesContext._

      def minBlankLinesAround(psi: PsiElement, context: BlankLinesContext): Int =
        (psi, context) match {
          case (_: ScFunction, Class)            => cs.BLANK_LINES_AROUND_METHOD
          case (_: ScFunction, Trait)            => cs.BLANK_LINES_AROUND_METHOD_IN_INTERFACE
          case (_: ScFunction, LocalScope)       => ss.BLANK_LINES_AROUND_METHOD_IN_INNER_SCOPES
          case (_: ScEnumCase, Class)            => 0 // TODO: add a setting for enum cases
          case (_: ScTypeDefinition, Class)      => cs.BLANK_LINES_AROUND_CLASS
          case (_: ScTypeDefinition, Trait)      => cs.BLANK_LINES_AROUND_CLASS
          case (_: ScTypeDefinition, LocalScope) => ss.BLANK_LINES_AROUND_CLASS_IN_INNER_SCOPES
          case (_: ScTypeDefinition, TopLevel)   => cs.BLANK_LINES_AROUND_CLASS
          case (_, Class)                        => cs.BLANK_LINES_AROUND_FIELD
          case (_, Trait)                        => cs.BLANK_LINES_AROUND_FIELD_IN_INTERFACE
          case (_, LocalScope)                   => ss.BLANK_LINES_AROUND_FIELD_IN_INNER_SCOPES
          case (_, TopLevel)                     => ss.BLANK_LINES_AROUND_FIELD_IN_INNER_SCOPES
          case _                                 => 0
        }

      val context: BlankLinesContext = leftPsiParent match {
        case _: ScEarlyDefinitions | _: ScTemplateBody =>
          val p = PsiTreeUtil.getParentOfType(leftPsiParent, classOf[ScTemplateDefinition])
          if (p.isInstanceOf[ScTrait]) Trait
          else Class
        case _: ScBlock                                  =>
          LocalScope
        case _: ScalaFile | _: ScPackaging =>
          TopLevel
        case _ =>
          null
      }

      if (context != null) {
        val leftValue = minBlankLinesAround(leftPsi, context)
        val rightValue = minBlankLinesAround(rightPsi, context)
        val minBlankLines = math.max(leftValue, rightValue)

        return Spacing.createSpacing(0, 0, minBlankLines + 1, keepLineBreaks, keepBlankLinesInDeclarations)
      }
    }

    // ';' from left
    // (semicolon before while in do/while statement shouldn't force-place while on a new line)
    // kWHILE is handled later
    if (leftElementType == ScalaTokenTypes.tSEMICOLON && rightElementType != kWHILE) {
      def isInsideForEnumerators: Boolean = {
        val rightTreeParentIsFile = rightNode.getTreeParent.getPsi.is[ScalaFile]
        val rightPsiParentIsFor = rightPsiParent.getParent.is[ScFor]
        !rightTreeParentIsFile && rightPsiParentIsFor
      }

      val result =
        if (isInsideForEnumerators) {
          if (settings.SPACE_AFTER_SEMICOLON) WITH_SPACING
          else WITHOUT_SPACING
        }
        // TODO: this behaviour was implemented long ago in 2008 but it doesn't work well
        //  First format of one-liner `def foo = { 1; 2; }` inserts new line, making it a non-one-liner
        //  Second format inserts new line after `;` after it
        //  Looks like it only works in case clause branch: 1 match { case _ => 1; 2; 3 }
        else if (rightElementType == ScalaTokenTypes.tSEMICOLON)
          NO_SPACING
        else if (!containsNewLine(fileText, leftNode.getTreeParent.getTextRange))
          WITH_SPACING
        else
          getSpacing(1, 1) // add spacing even if new line is inserted in order backspace works find when merging lines
      return result
    }

    //special else if treatment
    val isElseIf = leftElementType == ScalaTokenTypes.kELSE &&
      (rightPsi.isInstanceOf[ScIf] || rightElementType == ScalaTokenTypes.kIF)
    if (isElseIf) {
      val spacing =
        if (settings.SPECIAL_ELSE_IF_TREATMENT) WITH_SPACING_NO_KEEP
        else ON_NEW_LINE
      return spacing
    }

    if (rightElementType == ScalaTokenTypes.kELSE && right.lastNode != null) {
      var lastNode = left.lastNode
      while (lastNode != null && lastNode.getPsi.isInstanceOf[PsiWhiteSpace])
        lastNode = lastNode.getTreePrev

      val spacing =
        if (lastNode == null) WITH_SPACING_DEPENDENT(rightNode.getTreeParent.getTextRange)
        else if (settings.ELSE_ON_NEW_LINE) ON_NEW_LINE
        else if (COMMENTS_TOKEN_SET.contains(lastNode.getElementType)) ON_NEW_LINE // SCL-16831
        else WITH_SPACING
      return spacing
    }

    if (leftElementType == ScalaElementType.MODIFIERS) {
      return if (rightPsi.isInstanceOf[ScParameters])
        if (scalaSettings.SPACE_AFTER_MODIFIERS_CONSTRUCTOR) WITH_SPACING
        else WITHOUT_SPACING
      else if (settings.MODIFIER_LIST_WRAP) WITH_SPACING_DEPENDENT(leftNode.getTreeParent.getTextRange)
      else WITH_SPACING
    }

    if (rightPsi.isInstanceOf[ScCatchBlock]) {
      return if (settings.CATCH_ON_NEW_LINE) ON_NEW_LINE
      else WITH_SPACING
    }

    if (rightPsi.isInstanceOf[ScFinallyBlock]) {
      return if (settings.FINALLY_ON_NEW_LINE) ON_NEW_LINE
      else WITH_SPACING
    }

    if (rightElementType == kWHILE) {
      return if (settings.WHILE_ON_NEW_LINE) WITH_SPACING_DEPENDENT(rightPsiParent.getTextRange)
      else WITH_SPACING
    }

    val atProcessor: PartialFunction[ASTNode, Spacing] = {
      case node if node.getElementType == tAT && node.getTreeParent != null &&
        node.getTreeParent.getElementType == ScalaElementType.NAMING_PATTERN =>
        if (scalaSettings.SPACES_AROUND_AT_IN_PATTERNS) WITH_SPACING
        else WITHOUT_SPACING
    }
    atProcessor.lift(rightNode) match {
      case Some(spacing) =>
        return spacing
      case _ =>
    }
    atProcessor.lift(leftNode) match {
      case Some(spacing) =>
        return spacing
      case _ =>
    }

    //FIXME: this is a quick hack to stop method signature in scalaDoc from getting disrupted. (#SCL-4280)
    // actually the DOC_COMMENT_BAD_CHARACTER elements seem out of place in here
    // also method references are now parsed incorrectly, e.g. Class#method() will contain normal reference for `Class`
    // and dangling identifiers `#` and `method` and bad characters for '(' and ')'
    if (leftNodeParentElementType.isInstanceOf[ScalaDocSyntaxElementType] && (
      leftElementType == ScalaDocTokenType.DOC_COMMENT_BAD_CHARACTER |
        rightElementType == ScalaDocTokenType.DOC_COMMENT_BAD_CHARACTER |
        leftElementType == ScalaTokenTypes.tIDENTIFIER |
        rightElementType == ScalaTokenTypes.tIDENTIFIER
      ))
      return Spacing.getReadOnlySpacing

    //old formatter spacing


    //comments processing
    if (leftPsi.isInstanceOf[ScDocComment])
      return ON_NEW_LINE
    if (rightPsi.isInstanceOf[ScDocComment] && leftElementType == ScalaTokenTypes.tLBRACE)
      return ON_NEW_LINE
    if (rightPsi.isInstanceOf[ScDocComment])
      return DOUBLE_LINE
    if (rightPsi.isInstanceOf[PsiComment])
      return COMMON_SPACING
    //extra check for `left.lastNode == null` is needed for cases when comment is first node in a larger block
    //For example (notice that this method call chain uses unusual syntax, when dot is palced on previos line)
    //myObject.
    //  /*comment*/ bar().bar()
    if (leftPsi.isInstanceOf[PsiComment] && (left.lastNode == null) )
      return COMMON_SPACING

    //; : . and , processing
    if (rightBlockString.startsWith(".") &&
      rightElementType != ScalaTokenType.Float &&
      rightElementType != ScalaTokenType.Double &&
      !rightPsi.isInstanceOf[ScLiteral]) {
      return WITHOUT_SPACING
    }
    if (rightBlockString.startsWith(",")) {
      return if (settings.SPACE_BEFORE_COMMA) WITH_SPACING
      else WITHOUT_SPACING
    }
    if (rightElementType == ScalaTokenTypes.tCOLON) {
      val result = rightPsiParent match {
        case tp: ScTypeParam =>
          val tpNode     = tp.nameId.getNode
          val tpNodeNext = tpNode.getTreeNext

          val isLeadingContextBound = tpNode.eq(leftNode)
          val isLeadingContextBoundHK = tpNodeNext.eq(leftNode) &&
            tpNodeNext != null && tpNodeNext.getElementType == ScalaElementType.TYPE_PARAM_CLAUSE

          if (isLeadingContextBound) {
            withSpacingIf(scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_LEADING_CONTEXT_BOUND_COLON)
          } else if (isLeadingContextBoundHK) {
            withSpacingIf(scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_LEADING_CONTEXT_BOUND_COLON_HK)
          } else {
            withSpacingIf(scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_REST_CONTEXT_BOUND_COLONS)
          }
        case _ =>
          if (scalaSettings.SPACE_BEFORE_TYPE_COLON) {
            WITH_SPACING
          } else {
            // For operations like `var Object_!= : Symbol = _`
            var left = leftNode
            while (left != null && left.getLastChildNode != null) {
              left = left.getLastChildNode
            }
            val colonCanStickToLeftIdentifier = left.getElementType == ScalaTokenTypes.tIDENTIFIER && isIdentifier(getText(left, fileText) + ":")
            withSpacingIf(colonCanStickToLeftIdentifier)
          }
      }
      return result
    }

    if (leftBlockString.endsWith(".")) {
      return leftElementType match {
        case ScalaElementType.StringLiteral |
             ScalaElementType.NullLiteral |
             ScalaElementType.LongLiteral |
             ScalaElementType.IntegerLiteral |
             ScalaElementType.DoubleLiteral |
             ScalaElementType.FloatLiteral |
             ScalaElementType.BooleanLiteral |
             ScalaElementType.SymbolLiteral |
             ScalaElementType.CharLiteral => WITH_SPACING
        case _ => WITHOUT_SPACING
      }
    }
    if (leftBlockString.endsWith(",")) {
      return if (settings.SPACE_AFTER_COMMA) WITH_SPACING
      else WITHOUT_SPACING
    }
    if (leftElementType == ScalaTokenTypes.tCOLON) {
      return if (scalaSettings.SPACE_AFTER_TYPE_COLON) WITH_SPACING
      else WITHOUT_SPACING
    }


    //processing left parenthesis (if it's from right) as Java cases
    if (rightElementType == ScalaTokenTypes.tLPARENTHESIS) {
      leftElementType match {
        case ScalaTokenTypes.kIF =>
          return if (settings.SPACE_BEFORE_IF_PARENTHESES) WITH_SPACING
          else WITHOUT_SPACING
        case ScalaTokenTypes.kWHILE =>
          return if (settings.SPACE_BEFORE_WHILE_PARENTHESES) WITH_SPACING
          else WITHOUT_SPACING
        case ScalaTokenTypes.kFOR =>
          return if (settings.SPACE_BEFORE_FOR_PARENTHESES) WITH_SPACING
          else WITHOUT_SPACING
        case _ =>
      }
    }
    if (rightPsi.isInstanceOf[ScParameters]) {
      leftPsiParent match {
        case _: ScGiven =>
          //add space between `given` keyword and parameters in anonymous given:
          //`given (using String): String = ???`
          val addSpacing = leftElementType == ScalaTokenType.GivenKeyword
          return if (addSpacing) WITH_SPACING else WITHOUT_SPACING
        case fun: ScFunction =>
          val addSpacing = settings.SPACE_BEFORE_METHOD_PARENTHESES ||
            (scalaSettings.SPACE_BEFORE_INFIX_LIKE_METHOD_PARENTHESES && ScalaNamesUtil.isOperatorName(fun.name)) ||
            (scalaSettings.PRESERVE_SPACE_AFTER_METHOD_DECLARATION_NAME && rightNode.getTreePrev.getPsi.isInstanceOf[PsiWhiteSpace])
          return if (addSpacing) WITH_SPACING else WITHOUT_SPACING
        case _: ScExtension =>
          //We could add some extring setting like we do for ScFunction, but lets not do that unless we will see the demand from users
          val result =
            if (leftElementType == ScalaTokenType.ExtensionKeyword) WITH_SPACING //extension (param: String)
            else WITHOUT_SPACING //extension [TypeParam](param: String)
          return result
        case _ =>
      }
    }

    if (rightPsi.is[ScArgumentExprList, ScPatternArgumentList] && (
      leftNode.getTreeParent.getPsi.is[ScMethodCall, ScConstructorInvocation, ScGenericCall] ||
        rightNode.getTreeParent.getPsi.is[ScSelfInvocation] && leftElementType == ScalaTokenTypes.kTHIS
      )) {
      val result =
        if (settings.SPACE_BEFORE_METHOD_CALL_PARENTHESES && !rightBlockString.startsWith("{") &&
          (leftNode.getLastChildNode == null || !leftNode.getLastChildNode.getPsi.isInstanceOf[ScArguments]) &&
          !leftPsi.isInstanceOf[ScArguments]) WITH_SPACING
        else if (scalaSettings.SPACE_BEFORE_BRACE_METHOD_CALL && rightBlockString.startsWith("{")) WITH_SPACING
        else WITHOUT_SPACING

      return result
    }

    if (rightNode.getTreeParent.getPsi.isInstanceOf[ScSelfInvocation] &&
      leftNode.getTreeParent.getPsi.isInstanceOf[ScSelfInvocation] && leftPsi.isInstanceOf[ScArguments] &&
      rightPsi.isInstanceOf[ScArguments]) {
      return WITHOUT_SPACING
    }
    // SCL-2601
    if (rightPsi.is[ScUnitExpr, ScTuple, ScParenthesisedExpr] && leftNode.getTreeParent.getPsi.isInstanceOf[ScInfixExpr]) {
      val isOperator = leftPsi match {
        case ref: ScReferenceExpression => ScalaNamesUtil.isOperatorName(ref.refName)
        case _ => false
      }

      val result =
        if (scalaSettings.SPACE_BEFORE_INFIX_METHOD_CALL_PARENTHESES || isOperator) WITH_SPACING
        else if (scalaSettings.SPACE_BEFORE_INFIX_OPERATOR_LIKE_METHOD_CALL_PARENTHESES && rightPsi.is[ScParenthesisedExpr]) WITH_SPACING
        else WITHOUT_SPACING

      return result
    }

    //processing left parenthesis (if it's from right) only Scala cases
    if (rightNode.getPsi.isInstanceOf[ScParameters] &&
      leftNode.getTreeParent.getPsi.isInstanceOf[ScPrimaryConstructor]) {
      if (settings.SPACE_BEFORE_METHOD_PARENTHESES || (scalaSettings.SPACE_BEFORE_INFIX_LIKE_METHOD_PARENTHESES &&
        ScalaNamesUtil.isOperatorName(leftNode.getTreeParent.getPsi.asInstanceOf[ScPrimaryConstructor].name)) ||
        (scalaSettings.PRESERVE_SPACE_AFTER_METHOD_DECLARATION_NAME &&
          rightNode.getTreePrev.getPsi.isInstanceOf[PsiWhiteSpace]))
        return WITH_SPACING
      else return WITHOUT_SPACING
    }
    rightNode.getPsi match {
      case _: ScPrimaryConstructor if rightBlockString.startsWith("(") =>
        if (settings.SPACE_BEFORE_METHOD_PARENTHESES ||
          (scalaSettings.SPACE_BEFORE_INFIX_LIKE_METHOD_PARENTHESES && ScalaNamesUtil.isOperatorName(leftBlockString)) ||
          (scalaSettings.PRESERVE_SPACE_AFTER_METHOD_DECLARATION_NAME &&
            rightNode.getTreePrev.getPsi.isInstanceOf[PsiWhiteSpace]))
          return WITH_SPACING
        else return WITHOUT_SPACING
      case _: ScPrimaryConstructor =>
        return WITH_SPACING
      case _ =>
    }
    if (leftPsi.isInstanceOf[ScParameterClause] && rightPsi.isInstanceOf[ScParameterClause]) {
      return WITHOUT_SPACING
    }
    if (rightPsi.isInstanceOf[ScPatternArgumentList] &&
      rightNode.getTreeParent.getPsi.isInstanceOf[ScConstructorPattern]) {
      return if (settings.SPACE_BEFORE_METHOD_CALL_PARENTHESES) WITH_SPACING
      else WITHOUT_SPACING
    }

    //processing left parenthesis (if it's from left)
    if (leftElementType == ScalaTokenTypes.tLPARENTHESIS) {
      if (rightElementType == ScalaTokenTypes.tRPARENTHESIS)
        return WITHOUT_SPACING
      else leftNode.getTreeParent.getPsi match {
        case _: ScFor if left.isLeaf =>
          if (settings.SPACE_WITHIN_FOR_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScIf =>
          if (settings.SPACE_WITHIN_IF_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScWhile | _: ScDo =>
          if (settings.SPACE_WITHIN_WHILE_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScParenthesisedExpr =>
          if (settings.SPACE_WITHIN_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case x: ScParameterClause if x.getParent.getParent.isInstanceOf[ScFunction] =>
          if (settings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case x: ScParameterClause if x.getParent.getParent.isInstanceOf[ScPrimaryConstructor] =>
          if (settings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScPatternArgumentList =>
          if (settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScArguments =>
          if (settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScParenthesisedPattern =>
          if (settings.SPACE_WITHIN_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScTuplePattern =>
          WITHOUT_SPACING //todo: add setting
        case _: ScParenthesisedTypeElement =>
          if (settings.SPACE_WITHIN_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScTupleTypeElement =>
          WITHOUT_SPACING //todo: add setting
        case _: ScTuple =>
          WITHOUT_SPACING //todo: add setting
        case _: ScFunctionalTypeElement =>
          if (settings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _ =>
      }
    }
    //processing right parenthesis (if it's from right)
    if (rightElementType == ScalaTokenTypes.tRPARENTHESIS) {
      if (leftElementType == ScalaTokenTypes.tLPARENTHESIS)
        return WITHOUT_SPACING
      rightNode.getTreeParent.getPsi match {
        case _: ScFor =>
          if (settings.SPACE_WITHIN_FOR_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScIf =>
          if (settings.SPACE_WITHIN_IF_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScWhile | _: ScDo =>
          if (settings.SPACE_WITHIN_WHILE_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScParenthesisedExpr =>
          if (settings.SPACE_WITHIN_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case x: ScParameterClause if x.getParent.getParent.isInstanceOf[ScFunction] =>
          if (settings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case x: ScParameterClause if x.getParent.getParent.isInstanceOf[ScPrimaryConstructor] =>
          if (settings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScPatternArgumentList =>
          if (settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScArguments =>
          if (settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScParenthesisedPattern =>
          if (settings.SPACE_WITHIN_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScTuplePattern =>
          WITHOUT_SPACING //todo: add setting
        case _: ScParenthesisedTypeElement =>
          if (settings.SPACE_WITHIN_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScTupleTypeElement =>
          WITHOUT_SPACING //todo: add setting
        case _: ScTuple =>
          WITHOUT_SPACING //todo: add setting
        case _: ScFunctionalTypeElement =>
          if (settings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _ =>
      }
    }

    //processing square brackets "[]"
    if (leftElementType == ScalaTokenTypes.tLSQBRACKET) {
      val result =
        if (rightElementType == ScalaTokenTypes.tRSQBRACKET)
          WITHOUT_SPACING
        else if (settings.SPACE_WITHIN_BRACKETS)
          WITH_SPACING
        else
          WITHOUT_SPACING
      return result
    }
    rightElementType match {
      case ScalaTokenTypes.tRSQBRACKET =>
        return if (settings.SPACE_WITHIN_BRACKETS)  WITH_SPACING else WITHOUT_SPACING
      case ScalaElementType.TYPE_PARAM_CLAUSE =>
        val result =
          //add space after `given` in anonymous given alias with type parameter:
          //given [T](using T): String = ??? //alias
          //given [T](using Int, Short): Ordering[Byte] with {} //definition
          if (leftPsiParent.is[ScGiven] && leftElementType == ScalaTokenType.GivenKeyword) WITH_SPACING
          //"extension [T]..."s
          else if (leftNodeParentElementType == EXTENSION) WITH_SPACING
          else if (scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_IN_DEF_LIST) WITH_SPACING
          else WITHOUT_SPACING
        return result
      case ScalaElementType.TYPE_ARGS  =>
        return if (settings.SPACE_BEFORE_TYPE_PARAMETER_LIST) WITH_SPACING else WITHOUT_SPACING
      case ScalaElementType.TYPE_LAMBDA =>
        return COMMON_SPACING
      case _ =>
        //continue
    }

    //special for "case <caret> =>" (for SurroundWith)
    if (leftElementType == ScalaTokenTypes.kCASE && rightElementType == ScalaTokenTypes.tFUNTYPE) {
      return Spacing.createSpacing(2, 2, 0, false, 0)
    }

    //`case _ => <caret> ...multiline body...`
    if (scalaSettings.NEW_LINE_AFTER_CASE_CLAUSE_ARROW_WHEN_MULTILINE_BODY) {
      val isArrowBeforeMultilineCaseClauseBody = leftElementType == ScalaTokenTypes.tFUNTYPE &&
        leftNodeParentElementType == ScalaElementType.CASE_CLAUSE &&
        rightElementType == ScalaElementType.BLOCK &&
        nodeTextContainsNewLine(rightNode, fileText)

      if (isArrowBeforeMultilineCaseClauseBody) {
        return ON_NEW_LINE
      }
    }

    //Case Clauses case
    if (leftElementType == ScalaElementType.CASE_CLAUSE && rightElementType == ScalaElementType.CASE_CLAUSE) {
      return WITH_SPACING_DEPENDENT(leftNode.getTreeParent.getTreeParent.getTextRange)
    }


    (leftElementType, rightElementType, leftNodeParentElementType, rightNodeParentElementType) match {
      case (ScalaTokenTypes.tFUNTYPE, ScalaElementType.BLOCK, ScalaElementType.FUNCTION_EXPR, _)
        if !scalaSettings.PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE =>
        if (rightBlockString.startsWith("{")) WITH_SPACING
        else if (containsNewLine(fileText, leftNode.getTreeParent.getTextRange)) ON_NEW_LINE
        else WITH_SPACING
      //type with annotation (val x: String @unchecked)
      case (_, ScalaElementType.ANNOTATIONS, ScalaElementType.ANNOT_TYPE, _) =>
        WITH_SPACING
      //case for package statement
      case (ScalaElementType.REFERENCE, ret, _, _) if ret != ScalaElementType.PACKAGING &&
        leftNode.getTreePrev != null && leftNode.getTreePrev.getTreePrev != null &&
        leftNode.getTreePrev.getTreePrev.getElementType == ScalaTokenTypes.kPACKAGE => DOUBLE_LINE
      case (ScalaElementType.REFERENCE, ScalaElementType.PACKAGING, _, _) if leftNode.getTreePrev != null &&
        leftNode.getTreePrev.getTreePrev != null &&
        leftNode.getTreePrev.getTreePrev.getElementType == ScalaTokenTypes.kPACKAGE => ON_NEW_LINE
      //case for covariant or contrvariant type params
      case (ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER, ScalaElementType.TYPE_PARAM, ScalaElementType.TYPE_PARAM) =>
        NO_SPACING

      //class params
      case (ScalaTokenTypes.tIDENTIFIER | ScalaElementType.TYPE_PARAM_CLAUSE, ScalaElementType.PRIMARY_CONSTRUCTOR, _, _)
        if rightPsi.asInstanceOf[ScPrimaryConstructor].annotations.isEmpty &&
          !rightPsi.asInstanceOf[ScPrimaryConstructor].hasModifier => NO_SPACING
      //Type*
      case (_, ScalaTokenTypes.tIDENTIFIER, _, ScalaElementType.PARAM_TYPE) if rightBlockString == "*" => NO_SPACING
      //Parameters
      case (ScalaTokenTypes.tIDENTIFIER, ScalaElementType.PARAM_CLAUSES, _, _) => NO_SPACING
      case (_, ScalaElementType.TYPE_ARGS, _, ScalaElementType.TYPE_GENERIC_CALL | ScalaElementType.GENERIC_CALL) =>
        NO_SPACING
      case (_, ScalaElementType.PATTERN_ARGS, _, ScalaElementType.CONSTRUCTOR_PATTERN) => NO_SPACING
      //Annotation
      case (ScalaTokenTypes.tAT, _, _, _) if rightPsi.isInstanceOf[ScXmlPattern] => WITH_SPACING
      case (ScalaTokenTypes.tAT, _, _, _) => NO_SPACING
      case (ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tAT, ScalaElementType.NAMING_PATTERN, _) => NO_SPACING
      // Scala named seq-pattern: case Seq(other*) =>
      case (ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tIDENTIFIER, ScalaElementType.SEQ_WILDCARD_PATTERN, _) if rightBlockString == "*" => NO_SPACING
      case (_, ScalaTokenTypes.tAT, _, _) => NO_SPACING_WITH_NEWLINE
      case (ScalaElementType.ANNOTATION, _, _, _) => COMMON_SPACING
      //Prefix Identifier
      case (ScalaElementType.REFERENCE_EXPRESSION |
            ScalaTokenTypes.tIDENTIFIER, _,
      ScalaElementType.StringLiteral |
      ScalaElementType.NullLiteral |
      ScalaElementType.LongLiteral |
      ScalaElementType.IntegerLiteral |
      ScalaElementType.DoubleLiteral |
      ScalaElementType.FloatLiteral |
      ScalaElementType.BooleanLiteral |
      ScalaElementType.SymbolLiteral |
      ScalaElementType.CharLiteral |
      ScalaElementType.PREFIX_EXPR, _) => NO_SPACING
      //Braces
      case (ScalaTokenTypes.tLBRACE, ScalaTokenTypes.tRBRACE, _, _) => NO_SPACING
      case (ScalaTokenTypes.tLBRACE, _, ScalaElementType.TEMPLATE_BODY |
                                        ScalaElementType.MATCH_STMT |
                                        ScalaElementType.REFINEMENT |
                                        ScalaElementType.EXISTENTIAL_CLAUSE |
                                        ScCodeBlockElementType.BlockExpression, _) => IMPORT_BETWEEN_SPACING
      case (ScalaTokenTypes.tLBRACE, _, _, _) => NO_SPACING_WITH_NEWLINE
      case (_, ScalaTokenTypes.tRBRACE, ScalaElementType.TEMPLATE_BODY |
                                        ScalaElementType.MATCH_STMT |
                                        ScalaElementType.REFINEMENT |
                                        ScalaElementType.EXISTENTIAL_CLAUSE |
                                        ScCodeBlockElementType.BlockExpression, _) => IMPORT_BETWEEN_SPACING
      case (_, ScalaTokenTypes.tRBRACE, _, _) => NO_SPACING_WITH_NEWLINE
      //Imports
      case (ImportStatement, ImportStatement, _, _) => IMPORT_BETWEEN_SPACING
      case (ImportStatement, _, _: ScStubFileElementType, _) => DOUBLE_LINE
      case (ImportStatement, _, ScalaElementType.PACKAGING, _) => DOUBLE_LINE
      case (ImportStatement, _, _, _) => IMPORT_BETWEEN_SPACING
      //Dot
      case (ScalaTokenTypes.tDOT, _, _, _) => NO_SPACING_WITH_NEWLINE
      case (_, ScalaTokenTypes.tDOT, _, _) => NO_SPACING
      //Comma
      case (ScalaTokenTypes.tCOMMA, _, _, _) => COMMON_SPACING
      case (_, ScalaTokenTypes.tCOMMA, _, _) => NO_SPACING
      //Parenthesises and Brackets
      case (ScalaTokenTypes.tLPARENTHESIS | ScalaTokenTypes.tLSQBRACKET, _, _, _) => NO_SPACING_WITH_NEWLINE
      case (_, ScalaTokenTypes.tLSQBRACKET, _, _) => NO_SPACING
      case (_, ScalaTokenTypes.tLPARENTHESIS, ScalaElementType.CONSTRUCTOR_PATTERN, _) => NO_SPACING
      case (ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tRSQBRACKET, _, _, _) => COMMON_SPACING
      case (_, ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tRSQBRACKET, _, _) => NO_SPACING_WITH_NEWLINE
      //Case clauses
      case (ScalaElementType.CASE_CLAUSE, _, _, _) =>
        IMPORT_BETWEEN_SPACING
      case (_, ScalaElementType.CASE_CLAUSE, _, _) =>
        // support for Scala3 single `case clause` on the same line with `catch`:
        // `try foo() catch case ex: Exception => println(42)`
        val isScala3_OnlyCaseClause = rightNode.getTreeNext == null && leftElementType == ScalaTokenTypes.kCATCH
        if (isScala3_OnlyCaseClause) COMMON_SPACING
        else IMPORT_BETWEEN_SPACING
      //#
      case (ScalaTokenTypes.tINNER_CLASS, _, _, _) => NO_SPACING
      case (ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER, _, _) =>
        leftPsi.getNextSibling match {
          case _: PsiWhiteSpace => COMMON_SPACING
          case _ => NO_SPACING
        }
      case (_, ScalaTokenTypes.tINNER_CLASS, _, _) => NO_SPACING
      case (ScalaElementType.ANNOTATIONS, ScalaTokenTypes.kDEF, _, _) if scalaSettings.NEWLINE_AFTER_ANNOTATIONS =>
        ON_NEW_LINE
      //Other cases
      case (ScalaTokenTypes.tSTUB, _, _, _) |
           (_, ScalaTokenTypes.tSTUB, _, _) => NO_SPACING_WITH_NEWLINE
      case _ =>
        COMMON_SPACING
    }
  }

  //TODO: duplicate?
  @inline
  private def containsNewLine(fileText: CharSequence, range: TextRange): Boolean =
    contains(fileText, range.getStartOffset, range.getEndOffset, '\n')

  private def contains(text: CharSequence, start: Int, end: Int, char: Char): Boolean = {
    val textLength = text.length

    var idx = start
    while (idx < end && idx < textLength) {
      if (text.charAt(idx) == char)
        return true
      idx += 1
    }

    false
  }

  private sealed trait BlankLinesContext
  private object BlankLinesContext {
    object Class extends BlankLinesContext
    object Trait extends BlankLinesContext
    object LocalScope extends BlankLinesContext
    object TopLevel extends BlankLinesContext
  }
}
