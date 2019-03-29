package org.jetbrains.plugins.scala
package lang
package formatting
package processors

import java.util.regex.Pattern

import com.intellij.formatting.Spacing
import com.intellij.lang.ASTNode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.{IElementType, TokenSet}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenTypes, ScalaTokenTypesEx, ScalaXmlTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.{ScCodeBlockElementType, ScalaElementType}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base._
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
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.{isIdentifier, isKeyword}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import org.jetbrains.plugins.scala.util.MultilineStringUtil

import scala.annotation.tailrec

object ScalaSpacingProcessor extends ScalaTokenTypes {
  private val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.lang.formatting.processors.ScalaSpacingProcessor")

  private val BLOCK_ELEMENT_TYPES = {
    import ScCodeBlockElementType.BlockExpression
    import ScalaElementType._
    TokenSet.create(BlockExpression, TEMPLATE_BODY, PACKAGING, TRY_BLOCK, MATCH_STMT, CATCH_BLOCK)
  }

  private def getText(node: ASTNode, fileText: CharSequence): String = {
    fileText.substring(node.getTextRange)
  }

  private def getText(psi: PsiElement, fileText: CharSequence): String = {
    getText(psi.getNode, fileText)
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
      case (ScalaElementType.INTERPOLATED_STRING_LITERAL, _) => 0
      case (_, ScalaElementType.INTERPOLATED_STRING_LITERAL) => 0
      case _ if textRange.contains(rightNode.getTextRange) && textRange.contains(leftNode.getTextRange) =>
        val left = fileText.substring(leftNode.getTextRange)
        val right = fileText.substring(rightNode.getTextRange)
        val concatString = left + right
        if (isIdentifier(concatString) || isKeyword(concatString)) 1
        else 0
      case _ => 0
    }
  }

  def getSpacing(left: ScalaBlock, right: ScalaBlock): Spacing = {
    val settings = right.commonSettings

    val keepBlankLinesInCode = settings.KEEP_BLANK_LINES_IN_CODE
    val keepLineBreaks = settings.KEEP_LINE_BREAKS
    val keepBlankLinesInDeclarations = settings.KEEP_BLANK_LINES_IN_DECLARATIONS
    val keepBlankLinesBeforeRBrace = settings.KEEP_BLANK_LINES_BEFORE_RBRACE
    def getSpacing(x: Int, y: Int, z: Int): Spacing = {
      if (keepLineBreaks) Spacing.createSpacing(y, y, z, true, x)
      else Spacing.createSpacing(y, y, z, false, 0)
    }
    if (left == null) {
      return getSpacing(keepBlankLinesInCode, 0, 0) //todo:
    }
    val scalaSettings: ScalaCodeStyleSettings =
      left.settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    def getDependentLFSpacing(x: Int, y: Int, range: TextRange) = {
      if (keepLineBreaks) Spacing.createDependentLFSpacing(y, y, range, true, x)
      else Spacing.createDependentLFSpacing(y, y, range, false, 0)
    }

    //new formatter spacing

    val leftNode = left.getNode
    val rightNode = right.getNode
    val leftElementType = leftNode.getElementType
    val rightElementType = rightNode.getElementType
    val leftPsi = leftNode.getPsi
    val rightPsi = rightNode.getPsi
    val leftPsiParent = leftNode.getPsi.getParent
    val rightPsiParent = rightNode.getPsi.getParent

    val fileText = PsiDocumentManager.getInstance(leftPsi.getProject).nullSafe
      .map(_.getDocument(leftPsi.getContainingFile))
      .map(_.getImmutableCharSequence)
      .getOrElse(leftPsi.getContainingFile.charSequence)

    val fileTextRange = new TextRange(0, fileText.length())

    /**
     * This is not nodes text! This is blocks text, which can be different from node.
     */
    val (leftBlockString, rightBlockString) =
      if (fileTextRange.contains(left.getTextRange) && fileTextRange.contains(right.getTextRange)) {
        (fileText.substring(left.getTextRange), fileText.substring(right.getTextRange))
      } else {
        LOG.error("File text: \n%s\n\nDoesn't contains nodes:\n(%s, %s)".format(fileText, leftPsi.getText, rightPsi.getText))
        (leftPsi.getText, rightPsi.getText)
      }

    val spacesMin: Integer = spacesToPreventNewIds(left, right, fileText, fileTextRange)
    val WITHOUT_SPACING = getSpacing(keepBlankLinesInCode, spacesMin, 0)
    val WITHOUT_SPACING_NO_KEEP = Spacing.createSpacing(spacesMin, spacesMin, 0, false, 0)
    val WITHOUT_SPACING_DEPENDENT = (range: TextRange) => getDependentLFSpacing(keepBlankLinesInCode, spacesMin, range)
    val WITH_SPACING = getSpacing(keepBlankLinesInCode, 1, 0)
    val WITH_SPACING_NO_KEEP = Spacing.createSpacing(1, 1, 0, false, 0)
    val WITH_SPACING_DEPENDENT = (range: TextRange) => getDependentLFSpacing(keepBlankLinesInCode, 1, range)
    val ON_NEW_LINE = getSpacing(keepBlankLinesInCode, 0, 1)
    val DOUBLE_LINE = getSpacing(keepBlankLinesInCode, 0, 2)

    val NO_SPACING_WITH_NEWLINE = Spacing.createSpacing(0, 0, 0, true, 1)
    val NO_SPACING = Spacing.createSpacing(spacesMin, spacesMin, 0, false, 0)
    val COMMON_SPACING = Spacing.createSpacing(1, 1, 0, keepLineBreaks, 100)
    val IMPORT_BETWEEN_SPACING = Spacing.createSpacing(0, 0, 1, true, 100)

    if (rightPsi.isInstanceOf[PsiComment] && settings.KEEP_FIRST_COLUMN_COMMENT)
      return Spacing.createKeepingFirstColumnSpacing(0, Integer.MAX_VALUE, true, settings.KEEP_BLANK_LINES_IN_CODE)

    import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
    if (leftPsi.isInstanceOf[PsiComment] && rightPsi.isInstanceOf[PsiComment]) {
      return ON_NEW_LINE
    }

    //ScalaDocs
    def docCommentOf(node: ASTNode) = node.getPsi.parentsInFile.instanceOf[ScDocComment].getOrElse {
      throw new RuntimeException("Unable to find parent doc comment")
    }

    def isScalaDocList(str: String) = str.startsWith("- ") || Pattern.matches("^([MDCLXVI]+|[a-zA-Z]+|\\d+)\\..+", str)

    val tagSpacing =
      if (scalaSettings.SD_PRESERVE_SPACES_IN_TAGS)
        Spacing.createSpacing(0, Int.MaxValue, 0, false, 0)
      else WITH_SPACING

    def processElementTypes(pf: PartialFunction[(IElementType, IElementType, IElementType, IElementType), Spacing]): Option[Spacing] =
      pf.lift((leftElementType, rightElementType, leftNode.getTreeParent.getElementType, rightNode.getTreeParent.getElementType))

    processElementTypes {
      case (_, ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS, _, _) => NO_SPACING_WITH_NEWLINE
      case (_, ScalaDocTokenType.DOC_COMMENT_END, _, _) =>
        if (docCommentOf(rightNode).version == 1) NO_SPACING_WITH_NEWLINE
        else if (leftBlockString(leftBlockString.length() - 1) != ' ') WITH_SPACING
        else WITHOUT_SPACING
      case (ScalaDocTokenType.DOC_COMMENT_START, _, _, _) =>
        if (docCommentOf(leftNode).version == 1) NO_SPACING_WITH_NEWLINE
        else if (getText(rightNode, fileText)(0) != ' ') WITH_SPACING
        else WITHOUT_SPACING
      case (x, y, _, _) if ScalaDocTokenType.ALL_SCALADOC_TOKENS.contains(x) &&
        ScalaDocTokenType.ALL_SCALADOC_TOKENS.contains(y) && !scalaSettings.ENABLE_SCALADOC_FORMATTING =>
        Spacing.getReadOnlySpacing
      case (ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS, _, _, _) =>
        if (isScalaDocList(getText(rightNode, fileText))) Spacing.getReadOnlySpacing
        else if (getText(rightNode, fileText).apply(0) == ' ') WITHOUT_SPACING
        else WITH_SPACING
      case (ScalaDocTokenType.DOC_TAG_NAME, _, _, _) =>
        val rightText = getText(rightNode, fileText) //rightString is not semantically equal for PsiError nodes
        if (rightText.nonEmpty && rightText.apply(0) == ' ') Spacing.getReadOnlySpacing
        else tagSpacing
      case (ScalaDocTokenType.DOC_TAG_VALUE_TOKEN, _, ScalaDocElementTypes.DOC_TAG, _) => tagSpacing
      case (_, x, _, _) if ScalaDocTokenType.ALL_SCALADOC_TOKENS.contains(x) => Spacing.getReadOnlySpacing
      case (x, TokenType.ERROR_ELEMENT, _, _) if ScalaDocTokenType.ALL_SCALADOC_TOKENS.contains(x) => WITH_SPACING
      case (x, _, _, _) if ScalaDocTokenType.ALL_SCALADOC_TOKENS.contains(x) => Spacing.getReadOnlySpacing
      case (ScalaTokenTypes.tLINE_COMMENT, _, _, _) => ON_NEW_LINE
    } match {
      case Some(result) => return result
      case _ =>
    }

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
    }  match {
      case Some(result) => return result
      case _ =>
    }

    def isParenthesisParent(psi: PsiElement): Boolean =
      psi.is[ScParenthesisedExpr, ScParameterizedTypeElement, ScParenthesisedPattern]

    if (leftElementType == tLPARENTHESIS && isParenthesisParent(leftPsiParent)) {
      return if (settings.PARENTHESES_EXPRESSION_LPAREN_WRAP)
        if (settings.SPACE_WITHIN_PARENTHESES) WITH_SPACING_DEPENDENT(leftPsiParent.getTextRange)
        else WITHOUT_SPACING_DEPENDENT(leftPsiParent.getTextRange)
      else if (settings.SPACE_WITHIN_PARENTHESES) WITH_SPACING
      else WITHOUT_SPACING
    }

    //for interpolated strings
    if (rightElementType == tINTERPOLATED_STRING_ESCAPE)
      return Spacing.getReadOnlySpacing
    if (Set(tINTERPOLATED_STRING, tINTERPOLATED_MULTILINE_STRING).contains(rightElementType))
      return if (leftBlockString == MultilineStringUtil.getMarginChar(leftPsi).toString) Spacing.getReadOnlySpacing
      else WITHOUT_SPACING
    if (Set(leftElementType, rightElementType).contains(tINTERPOLATED_STRING_INJECTION) || rightElementType == tINTERPOLATED_STRING_END)
      return Spacing.getReadOnlySpacing
    if (Option(leftNode.getTreeParent.getTreePrev).exists(_.getElementType == tINTERPOLATED_STRING_ID))
      return Spacing.getReadOnlySpacing

    @tailrec
    def isMultiLineStringCase(psiElem: PsiElement): Boolean = {
      psiElem match {
        case ml: ScLiteral if ml.isMultiLineString =>
          right.getTextRange.contains(new TextRange(rightNode.getTextRange.getStartOffset, rightNode.getTextRange.getStartOffset + 3))
        case _: ScInfixExpr | _: ScReferenceExpression | _: ScMethodCall => isMultiLineStringCase(psiElem.getFirstChild)
        case _ => false
      }
    }

    //multiline strings
    if (scalaSettings.MULTILINE_STRING_SUPORT != ScalaCodeStyleSettings.MULTILINE_STRING_NONE && isMultiLineStringCase(rightPsi)) {
      (scalaSettings.MULTI_LINE_QUOTES_ON_NEW_LINE, scalaSettings.KEEP_MULTI_LINE_QUOTES) match {
        case (false, true) =>
          val prevIsNewLine = rightPsi.getPrevSibling != null && getText(rightPsi.getPrevSibling.getNode, fileText).contains("\n")
          return if (prevIsNewLine) ON_NEW_LINE
          else WITH_SPACING
        case (true, false) => return ON_NEW_LINE
        case (false, false) => return WITH_SPACING_NO_KEEP
        case (true, true) =>
          //TODO the '0' in arguments is a temporary fix for SCL-8683: will not remove redundant space, but does not place new space either
          return Spacing.createDependentLFSpacing(0, 1, rightPsiParent.getTextRange, true, 1)
      }
    }

    def allLinesHaveMargin(literalText: String) = literalText.split("\n").map(_.trim).
      forall(line => line.startsWith("|") || line.startsWith("\"\"\""))

    leftPsi match {
      case l: ScLiteral if l.isMultiLineString && rightNode == leftNode =>
        val marginChar = "" + MultilineStringUtil.getMarginChar(leftPsi)

        return if (allLinesHaveMargin(fileText.substring(l.getTextRange)) &&
          (leftBlockString != marginChar || rightBlockString == marginChar)) NO_SPACING_WITH_NEWLINE else Spacing.getReadOnlySpacing
      case _ =>
    }

    val rightIsLineComment =
      rightElementType == ScalaTokenTypes.tLINE_COMMENT ||
        FormatterUtil.isCommentGrabbingPsi(rightPsi) && rightPsi.getFirstChild.elementType == ScalaTokenTypes.tLINE_COMMENT
    val noNewLineBetweenBlocks =
      !leftPsi.nextSibling.filterByType[PsiWhiteSpace].exists(getText(_, fileText).contains("\n"))
    if (scalaSettings.KEEP_COMMENTS_ON_SAME_LINE && rightIsLineComment && noNewLineBetweenBlocks) {
      return COMMON_SPACING
    }

    if (rightElementType == tRPARENTHESIS && isParenthesisParent(rightPsiParent)) {
      return if (settings.PARENTHESES_EXPRESSION_RPAREN_WRAP)
        if (settings.SPACE_WITHIN_PARENTHESES) WITH_SPACING_DEPENDENT(rightPsiParent.getTextRange)
        else WITHOUT_SPACING_DEPENDENT(rightPsiParent.getTextRange)
      else if (settings.SPACE_WITHIN_PARENTHESES) WITH_SPACING
      else WITHOUT_SPACING
    }
    if (leftElementType == tIDENTIFIER &&
      rightPsi.isInstanceOf[ScArgumentExprList] && !getText(rightNode, fileText).trim.startsWith("{")) {
      return if (settings.SPACE_BEFORE_METHOD_CALL_PARENTHESES) WITH_SPACING
      else WITHOUT_SPACING
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
        settings.SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES && rightElementType == tRPARENTHESIS) WITH_SPACING
      else WITHOUT_SPACING
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

    //todo: spacing for early definitions
    if (getText(rightNode, fileText).trim.startsWith("{")) {
      val result =
        if (rightPsi.isInstanceOf[ScImportSelectors]) WITHOUT_SPACING
        else if (leftPsiParent.isInstanceOf[ScParenthesisedTypeElement]) WITHOUT_SPACING
        else if (rightPsi.is[ScExtendsBlock, ScEarlyDefinitions, ScTemplateBody]) {
          settings.CLASS_BRACE_STYLE match {
            case CommonCodeStyleSettings.END_OF_LINE =>
              val deepestLast = PsiTreeUtil.getDeepestLast(left.lastNode.nullSafe.map(_.getPsi).getOrElse(leftPsi))
              val leftLineComment = deepestLast.getNode.nullSafe.exists(_.getElementType == tLINE_COMMENT)
              if (settings.SPACE_BEFORE_CLASS_LBRACE)
                if (leftLineComment) WITH_SPACING
                else WITH_SPACING_NO_KEEP
              else if (leftLineComment) WITHOUT_SPACING
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
          case _: ScBlock | _: ScEarlyDefinitions | _: ScTemplateBody if !rightPsiParent.isInstanceOf[ScTryBlock] => ON_NEW_LINE
          case _: ScArgumentExprList if rightPsi.isInstanceOf[ScBlock] => WITH_SPACING //don't add/remove newlines for partial function arguments
          case parent =>
            val (needSpace, braceStyle, startElement) =
              parent match {
                case fun: ScFunction =>
                  (settings.SPACE_BEFORE_METHOD_LBRACE, settings.METHOD_BRACE_STYLE, fun.nameId)
                case _: ScMethodCall if rightPsi.isInstanceOf[ScArguments] =>
                  (scalaSettings.SPACE_BEFORE_BRACE_METHOD_CALL, settings.BRACE_STYLE, parent)
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

    //this is a dirty hack for SCL-9264. It looks bad, but seems to be the only fast way to make this work.
    (leftElementType, leftPsi.getPrevSiblingNotWhitespace) match {
      case (ScalaTokenTypes.tLBRACE | ScalaTokenTypes.tLPARENTHESIS, forNode: LeafPsiElement) if !left.isLeaf() &&
        forNode.getElementType == ScalaTokenTypes.kFOR => return COMMON_SPACING
      case _ =>
    }

    if (leftPsi.isInstanceOf[ScStableCodeReference] && !rightPsi.isInstanceOf[ScPackaging]) {
      leftPsiParent match {
        case p: ScPackaging if p.reference.contains(leftPsi) =>
          if (rightElementType != ScalaTokenTypes.tSEMICOLON && rightElementType != ScalaTokenTypes.tLBRACE) {
            return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AFTER_PACKAGE + 1, keepLineBreaks, keepBlankLinesInCode)
          }
        case _ =>
      }
    }

    if (leftPsi.isInstanceOf[ScPackaging]) {
      if (rightElementType != ScalaTokenTypes.tSEMICOLON) {
        return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AFTER_PACKAGE + 1, keepLineBreaks, keepBlankLinesInCode)
      }
    }

    if (rightPsi.isInstanceOf[ScPackaging]) {
      return if (leftPsi.isInstanceOf[ScStableCodeReference] || leftElementType == tLBRACE)
        Spacing.createSpacing(0, 0, 1, keepLineBreaks, keepBlankLinesInCode)
      else
        Spacing.createSpacing(0, 0, settings.BLANK_LINES_BEFORE_PACKAGE + 1, keepLineBreaks, keepBlankLinesInCode)
    }

    if (leftPsi.isInstanceOf[ScImportStmt] && !rightPsi.isInstanceOf[ScImportStmt]) {
      if (rightElementType != ScalaTokenTypes.tSEMICOLON) {
        if (leftPsiParent.is[ScEarlyDefinitions, ScTemplateBody, ScalaFile, ScPackaging]) {
          return if (rightElementType == ScalaTokenTypes.tLINE_COMMENT) ON_NEW_LINE
          else Spacing.createSpacing(0, 0, settings.BLANK_LINES_AFTER_IMPORTS + 1, keepLineBreaks, keepBlankLinesInCode)
        }
      }
      else if (settings.SPACE_BEFORE_SEMICOLON) return WITH_SPACING
      else return WITHOUT_SPACING
    }

    if (rightPsi.isInstanceOf[ScImportStmt] && !leftPsi.isInstanceOf[ScImportStmt]) {
      if (leftElementType != ScalaTokenTypes.tSEMICOLON || !leftPsi.getPrevSiblingNotWhitespace.isInstanceOf[ScImportStmt]) {
        if(rightPsiParent.is[ScEarlyDefinitions, ScTemplateBody, ScalaFile, ScPackaging]) {
          return Spacing.createSpacing(0, 0, settings.BLANK_LINES_BEFORE_IMPORTS + 1, keepLineBreaks, keepBlankLinesInCode)
        }
      }
    }

    if (leftPsi.isInstanceOf[ScImportStmt] || rightPsi.isInstanceOf[ScImportStmt]) {
      return Spacing.createSpacing(0, 0, 1, keepLineBreaks, keepBlankLinesInDeclarations)
    }

    if (leftPsi.isInstanceOf[ScTypeDefinition]) {
      if(rightElementType != ScalaTokenTypes.tSEMICOLON){
        if(leftPsiParent.is[ScEarlyDefinitions, ScTemplateBody, ScalaFile, ScPackaging]) {
          return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AROUND_CLASS + 1, keepLineBreaks, keepBlankLinesInDeclarations)
        }
      }
    }

    if (rightPsi.isInstanceOf[ScTypeDefinition]) {
      if (leftPsi.isInstanceOf[PsiComment]) {
        return ON_NEW_LINE
      }
      if(rightPsiParent.is[ScEarlyDefinitions, ScTemplateBody, ScalaFile, ScPackaging]) {
        return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AROUND_CLASS + 1, keepLineBreaks, keepBlankLinesInDeclarations)
      }
    }

    if (rightPsi.isInstanceOf[PsiComment]) {
      val pseudoRightPsi = rightPsi.getNextSiblingNotWhitespaceComment
      if (pseudoRightPsi.isInstanceOf[ScTypeDefinition]) {
        if(pseudoRightPsi.getParent.is[ScEarlyDefinitions, ScTemplateBody, ScalaFile, ScPackaging]) {
          return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AROUND_CLASS + 1, keepLineBreaks, keepBlankLinesInDeclarations)
        }
      }
    }

    if (rightElementType == ScalaTokenTypes.tRBRACE) {
      val rightTreeParent = rightNode.getTreeParent
      return rightTreeParent.getPsi match {
        case block@(_: ScEarlyDefinitions | _: ScTemplateBody | _: ScPackaging | _: ScBlockExpr | _: ScMatch |
                    _: ScTryBlock | _: ScCatchBlock) =>
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
                  .exists(_.getElementType == ScalaElementType.INTERPOLATED_STRING_LITERAL)
                scalaSettings.KEEP_ONE_LINE_LAMBDAS_IN_ARG_LIST &&
                  (leftPsi.is[ScFunctionExpr, ScCaseClauses] || block.isInstanceOf[ScBlockExpr] && !insideInterpString)
            })
            val isOneLineEmpty = leftBlockString == "{" || getText(block.getNode, fileText).contains('\n')
            !isOneLineEmpty && (scalaSettings.SPACES_IN_ONE_LINE_BLOCKS || inMethod || inSelfTypeBraces || inClosure)
          }

          val needsSpace = checkKeepOneLineLambdas || checkOneLineSpaces
          val spaces = if (needsSpace) 1 else 0
          Spacing.createDependentLFSpacing(spaces, spaces, block.getTextRange, keepLineBreaks, keepBlankLinesBeforeRBrace)
        case _: ScImportSelectors =>
          val refRange = leftNode.getTreeParent.getTextRange
          if (scalaSettings.SPACES_IN_IMPORTS) WITH_SPACING_DEPENDENT(refRange)
          else WITHOUT_SPACING_DEPENDENT(refRange)
        case _ =>
          Spacing.createSpacing(0, 0, 0, keepLineBreaks, keepBlankLinesBeforeRBrace)
      }
    }

    if (leftElementType == ScalaTokenTypes.tLBRACE) {
      if (!scalaSettings.PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE) {
        val b = leftNode.getTreeParent.getPsi
        val spaceInsideOneLineBlock = scalaSettings.SPACES_IN_ONE_LINE_BLOCKS && !getText(b.getNode, fileText).contains('\n')
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
        case b @ (_: ScEarlyDefinitions | _: ScTemplateBody) =>
          if (settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE && !getText(b.getNode, fileText).contains('\n')) {
            Spacing.createDependentLFSpacing(0, 0, b.getTextRange, keepLineBreaks, keepBlankLinesBeforeRBrace)
          } else {
            val c = PsiTreeUtil.getParentOfType(b, classOf[ScTemplateDefinition])
            val minLineFeeds = if (c.isInstanceOf[ScTypeDefinition]) settings.BLANK_LINES_AFTER_CLASS_HEADER
            else settings.BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER
            Spacing.createSpacing(0, 0, minLineFeeds + 1, keepLineBreaks, keepBlankLinesInDeclarations)
          }
        case b: ScBlockExpr if b.getParent.isInstanceOf[ScFunction] =>
          if (settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE && !getText(b.getNode, fileText).contains('\n')) {
            val spaces = if (scalaSettings.SPACES_IN_ONE_LINE_BLOCKS) 1 else 0
            Spacing.createDependentLFSpacing(spaces, spaces, b.getTextRange, keepLineBreaks, keepBlankLinesBeforeRBrace)
          } else {
            Spacing.createSpacing(0, 0, settings.BLANK_LINES_BEFORE_METHOD_BODY + 1, keepLineBreaks, keepBlankLinesInDeclarations)
          }
        case b: ScBlockExpr if scalaSettings.KEEP_ONE_LINE_LAMBDAS_IN_ARG_LIST &&
          !getText(b.getNode, fileText).contains('\n') && (rightPsi.isInstanceOf[ScCaseClauses] && b.getParent != null &&
          b.getParent.isInstanceOf[ScArgumentExprList] || rightPsi.isInstanceOf[ScFunctionExpr]) =>
          Spacing.createDependentLFSpacing(1, 1, b.getTextRange, keepLineBreaks, keepBlankLinesBeforeRBrace)
        case b: ScBlockExpr if scalaSettings.SPACE_INSIDE_CLOSURE_BRACES && !getText(b.getNode, fileText).contains('\n') &&
          scalaSettings.KEEP_ONE_LINE_LAMBDAS_IN_ARG_LIST && b.getParent.is[ScArgumentExprList, ScInfixExpr] =>
          WITH_SPACING
        case block@(_: ScPackaging | _: ScBlockExpr | _: ScMatch | _: ScTryBlock | _: ScCatchBlock) =>
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

    if (leftPsi.is[ScFunction, ScValueOrVariable, ScTypeAlias, ScExpression]) {
      if (rightElementType != tSEMICOLON) {
        leftPsiParent match {
          case b@(_: ScEarlyDefinitions | _: ScTemplateBody) =>
            val p = PsiTreeUtil.getParentOfType(b, classOf[ScTemplateDefinition])
            val setting = (leftPsi, rightPsi) match {
              case (_: ScFunction, _: ScValueOrVariable) | (_: ScValueOrVariable, _: ScFunction) |
                   (_: ScTypeAlias, _: ScFunction) | (_: ScFunction, _: ScTypeAlias) =>
                if (p.isInstanceOf[ScTrait])
                  math.max(settings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE, settings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE)
                else
                  math.max(settings.BLANK_LINES_AROUND_FIELD, settings.BLANK_LINES_AROUND_METHOD)
              case (_: ScFunction, _) | (_, _: ScFunction) =>
                if (p.isInstanceOf[ScTrait]) settings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE
                else settings.BLANK_LINES_AROUND_METHOD
              case _ =>
                if (p.isInstanceOf[ScTrait]) settings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE
                else settings.BLANK_LINES_AROUND_FIELD
            }
            val newLineBetween = fileText.substring(leftPsi.getTextRange.getEndOffset, rightPsi.getTextRange.getEndOffset).contains("\n")
            return if (rightPsi.isInstanceOf[PsiComment] && !newLineBetween) COMMON_SPACING
            else Spacing.createSpacing(0, 0, setting + 1, keepLineBreaks, keepBlankLinesInDeclarations)
          case _: ScBlock if rightPsi.isInstanceOf[PsiComment] =>
          case _: ScBlock =>
            val setting = (leftPsi, rightPsi) match {
              case (_: ScFunction, _: ScValueOrVariable) | (_: ScValueOrVariable, _: ScFunction) |
                   (_: ScTypeAlias, _: ScFunction) | (_: ScFunction, _: ScTypeAlias) =>
                math.max(scalaSettings.BLANK_LINES_AROUND_FIELD_IN_INNER_SCOPES, scalaSettings.BLANK_LINES_AROUND_METHOD_IN_INNER_SCOPES)
              case (_: ScFunction, _) | (_, _: ScFunction) => scalaSettings.BLANK_LINES_AROUND_METHOD_IN_INNER_SCOPES
              case _ => scalaSettings.BLANK_LINES_AROUND_FIELD_IN_INNER_SCOPES
            }
            return Spacing.createSpacing(0, 0, setting + 1, keepLineBreaks, keepBlankLinesInDeclarations)
          case _ =>
        }

      }
    }

    // TODO: how to name it normally?
    def spacingMagicProcessor(psi: PsiElement): Option[Spacing] = {
      psi.getParent match {
        case b @ (_: ScEarlyDefinitions | _: ScTemplateBody) =>
          val p = PsiTreeUtil.getParentOfType(b, classOf[ScTemplateDefinition])
          val setting = (psi, p) match {
            case (_: ScFunction, _: ScTrait) => settings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE
            case (_: ScFunction, _) => settings.BLANK_LINES_AROUND_METHOD
            case (_, _: ScTrait) => settings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE
            case _ => settings.BLANK_LINES_AROUND_FIELD
          }
          Some(Spacing.createSpacing(0, 0, setting + 1, keepLineBreaks, keepBlankLinesInDeclarations))
        case _ =>
          None
      }
    }

    if (rightPsi.isInstanceOf[PsiComment]) {
      val pseudoRightPsi = rightPsi.getNextSiblingNotWhitespaceComment
      if (pseudoRightPsi.is[ScFunction, ScValueOrVariable, ScTypeAlias]) {
        spacingMagicProcessor(pseudoRightPsi) match {
          case Some(spacing) => return spacing
          case _ =>
        }
      }
    }
    if (rightPsi.is[ScFunction, ScValueOrVariable, ScTypeAlias]) {
      if (leftPsi.isInstanceOf[PsiComment]) {
        return ON_NEW_LINE
      }
      spacingMagicProcessor(rightPsi) match {
        case Some(spacing) => return spacing
        case _ =>
      }
    }

    //special else if treatment
    val isElseIf = leftElementType == ScalaTokenTypes.kELSE &&
      (rightPsi.isInstanceOf[ScIf] || rightElementType == ScalaTokenTypes.kIF)
    if (isElseIf) {
      val isCommentAfterElse = Option(leftPsi.getNextSiblingNotWhitespace).exists(_.isInstanceOf[PsiComment])
      return if (settings.SPECIAL_ELSE_IF_TREATMENT && !isCommentAfterElse) WITH_SPACING_NO_KEEP
      else ON_NEW_LINE
    }

    if (rightElementType == ScalaTokenTypes.kELSE && right.lastNode != null) {
      var lastNode = left.lastNode
      while (lastNode != null && lastNode.getPsi.isInstanceOf[PsiWhiteSpace]) lastNode = lastNode.getTreePrev

      return if (lastNode == null) WITH_SPACING_DEPENDENT(rightNode.getTreeParent.getTextRange)
      else if (settings.ELSE_ON_NEW_LINE) ON_NEW_LINE
      else WITH_SPACING
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
      return if (settings.FINALLY_ON_NEW_LINE)  ON_NEW_LINE
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
      case Some(spacing) => return spacing
      case _ =>
    }
    atProcessor.lift(leftNode) match {
      case Some(spacing) => return spacing
      case _ =>
    }

    if (leftElementType == ScalaDocTokenType.DOC_COMMENT_BAD_CHARACTER ||
      rightElementType == ScalaDocTokenType.DOC_COMMENT_BAD_CHARACTER) {
      //FIXME: this is a quick hack to stop method signature in scalaDoc from getting disrupted. (#SCL-4280)
      //actually the DOC_COMMENT_BAD_CHARACTER elements seem out of place in here
      return Spacing.getReadOnlySpacing
    }

    //old formatter spacing


    //comments processing
    if (leftPsi.isInstanceOf[ScDocComment]) return ON_NEW_LINE
    if (rightPsi.isInstanceOf[ScDocComment] && leftElementType == ScalaTokenTypes.tLBRACE) return ON_NEW_LINE
    if (rightPsi.isInstanceOf[ScDocComment]) return DOUBLE_LINE
    if (rightPsi.isInstanceOf[PsiComment] || leftPsi.isInstanceOf[PsiComment]) return COMMON_SPACING
    //; : . and , processing
    if (rightBlockString.startsWith(".") &&
      rightElementType != ScalaTokenTypes.tFLOAT && !rightPsi.isInstanceOf[ScLiteral]) {
      return WITHOUT_SPACING
    }
    if (rightBlockString.startsWith(",")) {
      return if (settings.SPACE_BEFORE_COMMA) WITH_SPACING
      else WITHOUT_SPACING
    }
    if (rightElementType == ScalaTokenTypes.tCOLON) {
      var left = leftNode
      // For operations like
      // var Object_!= : Symbol = _
      if (scalaSettings.SPACE_BEFORE_TYPE_COLON) return WITH_SPACING  //todo:
      while (left != null && left.getLastChildNode != null) {
        left = left.getLastChildNode
      }
      val tp = PsiTreeUtil.getParentOfType(left.getPsi, classOf[ScTypeParam])
      if (tp != null) {
        return if (tp.nameId.getNode eq left) WITHOUT_SPACING
        else WITH_SPACING
      }
      val leftIsIdentifier = left.getElementType == ScalaTokenTypes.tIDENTIFIER && isIdentifier(getText(left, fileText) + ":")
      return if (leftIsIdentifier) WITH_SPACING
      else WITHOUT_SPACING
    }

    val rightTreeParentIsFile = rightNode.getTreeParent.getPsi.isInstanceOf[ScalaFile]
    val rightPsiParentIsFor = rightPsiParent.getParent.isInstanceOf[ScFor]
    val magicCondition = !rightTreeParentIsFile && rightPsiParentIsFor

    if (rightBlockString.startsWith(";")) {
      if (settings.SPACE_BEFORE_SEMICOLON && magicCondition) return WITH_SPACING
      else if (magicCondition) return WITHOUT_SPACING
    }
    if (leftBlockString.endsWith(".")) {
      return if (leftElementType == ScalaElementType.LITERAL) WITH_SPACING
      else WITHOUT_SPACING
    }
    if (leftBlockString.endsWith(",")) {
      return if (settings.SPACE_AFTER_COMMA) WITH_SPACING
      else WITHOUT_SPACING
    }
    if (leftElementType == ScalaTokenTypes.tCOLON) {
      return if (scalaSettings.SPACE_AFTER_TYPE_COLON) WITH_SPACING
      else WITHOUT_SPACING
    }
    if (leftBlockString.endsWith(";")) {
      if (settings.SPACE_AFTER_SEMICOLON && magicCondition) return WITH_SPACING
      else if (magicCondition) return WITHOUT_SPACING
    }

    if (leftElementType == ScalaTokenTypes.tSEMICOLON) {
      if (getText(leftNode.getTreeParent, fileText).indexOf('\n') == -1) return WITH_SPACING
      else ON_NEW_LINE // TODO: shouldn't here be a `return`?
    }

    //processing left parenthesis (if it's from right) as Java cases
    if (rightElementType == ScalaTokenTypes.tLPARENTHESIS) {
      leftElementType match {
        case ScalaTokenTypes.kIF =>
          return if (settings.SPACE_BEFORE_IF_PARENTHESES)  WITH_SPACING
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
    if (rightPsi.isInstanceOf[ScParameters] &&
      leftNode.getTreeParent.getPsi.isInstanceOf[ScFunction]) {
      if (settings.SPACE_BEFORE_METHOD_PARENTHESES || (scalaSettings.SPACE_BEFORE_INFIX_LIKE_METHOD_PARENTHESES &&
        ScalaNamesUtil.isOperatorName(leftNode.getTreeParent.getPsi.asInstanceOf[ScFunction].name)) ||
        (scalaSettings.PRESERVE_SPACE_AFTER_METHOD_DECLARATION_NAME &&
          rightNode.getTreePrev.getPsi.isInstanceOf[PsiWhiteSpace]))
        return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (rightPsi.isInstanceOf[ScArguments] &&
      leftNode.getTreeParent.getPsi.is[ScMethodCall, ScConstructorInvocation, ScGenericCall] ||
      rightPsi.isInstanceOf[ScArguments] && rightNode.getTreeParent.getPsi.isInstanceOf[ScSelfInvocation] &&
        getText(leftNode, fileText) == "this") {
      return if (settings.SPACE_BEFORE_METHOD_CALL_PARENTHESES && !rightBlockString.startsWith("{") &&
        (leftNode.getLastChildNode == null || !leftNode.getLastChildNode.getPsi.isInstanceOf[ScArguments]) &&
        !leftPsi.isInstanceOf[ScArguments]) WITH_SPACING
      else if (scalaSettings.SPACE_BEFORE_BRACE_METHOD_CALL && rightBlockString.startsWith("{")) WITH_SPACING
      else if (settings.SPACE_BEFORE_TYPE_PARAMETER_LIST && rightBlockString.startsWith("[")) WITH_SPACING
      else WITHOUT_SPACING
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
      return WITHOUT_SPACING //todo: add setting
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

    //proccessing sqbrackets
    if (leftElementType == ScalaTokenTypes.tLSQBRACKET) {
      if (rightElementType == ScalaTokenTypes.tRSQBRACKET) {
        return WITHOUT_SPACING
      }
      else {
        if (settings.SPACE_WITHIN_BRACKETS) return WITH_SPACING
        else return WITHOUT_SPACING
      }
    }
    if (rightElementType == ScalaTokenTypes.tRSQBRACKET) {
      if (settings.SPACE_WITHIN_BRACKETS) return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (rightBlockString.startsWith("[")) {
      return if (scalaSettings.SPACE_BEFORE_TYPE_PARAMETER_IN_DEF_LIST) WITH_SPACING else WITHOUT_SPACING
    }

    //special for "case <caret> =>" (for SurroundWith)
    if (leftElementType == ScalaTokenTypes.kCASE && rightElementType == ScalaTokenTypes.tFUNTYPE) {
      return Spacing.createSpacing(2, 2, 0, false, 0)
    }

    //Case Clauses case
    if (leftElementType == ScalaElementType.CASE_CLAUSE && rightElementType == ScalaElementType.CASE_CLAUSE) {
      return WITH_SPACING_DEPENDENT(leftNode.getTreeParent.getTreeParent.getTextRange)
    }

    (leftElementType, rightElementType,
      leftNode.getTreeParent.getElementType, rightNode.getTreeParent.getElementType) match {
      case (ScalaTokenTypes.tFUNTYPE, ScalaElementType.BLOCK, ScalaElementType.FUNCTION_EXPR, _)
        if !scalaSettings.PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE =>
        if (rightBlockString.startsWith("{")) WITH_SPACING
        else if (fileText.substring(leftNode.getTreeParent.getTextRange).contains("\n")) ON_NEW_LINE
        else WITH_SPACING
      //annotation
      case (_, ScalaElementType.ANNOTATIONS, ScalaElementType.ANNOT_TYPE, _) => WITHOUT_SPACING
      //case for package statement
      case (ScalaElementType.REFERENCE, ret, _, _) if ret != ScalaElementType.PACKAGING &&
        leftNode.getTreePrev != null && leftNode.getTreePrev.getTreePrev != null &&
        leftNode.getTreePrev.getTreePrev.getElementType == ScalaTokenTypes.kPACKAGE => DOUBLE_LINE
      case (ScalaElementType.REFERENCE, ScalaElementType.PACKAGING, _, _) if leftNode.getTreePrev != null &&
        leftNode.getTreePrev.getTreePrev != null &&
        leftNode.getTreePrev.getTreePrev.getElementType == ScalaTokenTypes.kPACKAGE => ON_NEW_LINE
      //case for covariant or contrvariant type params
      case (ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tIDENTIFIER, ScalaElementType.TYPE_PARAM, ScalaElementType.TYPE_PARAM) => NO_SPACING

      //class params
      case (ScalaTokenTypes.tIDENTIFIER | ScalaElementType.TYPE_PARAM_CLAUSE, ScalaElementType.PRIMARY_CONSTRUCTOR, _, _)
        if rightPsi.asInstanceOf[ScPrimaryConstructor].annotations.isEmpty &&
          !rightPsi.asInstanceOf[ScPrimaryConstructor].hasModifier => NO_SPACING
      //Type*
      case (_, ScalaTokenTypes.tIDENTIFIER, _, ScalaElementType.PARAM_TYPE) if rightBlockString == "*" => NO_SPACING
      //Parameters
      case (ScalaTokenTypes.tIDENTIFIER, ScalaElementType.PARAM_CLAUSES, _, _) => NO_SPACING
      case (_, ScalaElementType.TYPE_ARGS, _, ScalaElementType.TYPE_GENERIC_CALL | ScalaElementType.GENERIC_CALL) => NO_SPACING
      case (_, ScalaElementType.PATTERN_ARGS, _, ScalaElementType.CONSTRUCTOR_PATTERN) => NO_SPACING
      //Annotation
      case (ScalaTokenTypes.tAT, _, _, _) if rightPsi.isInstanceOf[ScXmlPattern] => WITH_SPACING
      case (ScalaTokenTypes.tAT, _, _, _) => NO_SPACING
      case (ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tAT, ScalaElementType.NAMING_PATTERN, _) => NO_SPACING
      case (_, ScalaTokenTypes.tAT, _, _) => NO_SPACING_WITH_NEWLINE
      case (ScalaElementType.ANNOTATION, _, _, _) => COMMON_SPACING
      //Prefix Identifier
      case (ScalaElementType.REFERENCE_EXPRESSION | ScalaTokenTypes.tIDENTIFIER, _,
      ScalaElementType.LITERAL | ScalaElementType.PREFIX_EXPR, _) => NO_SPACING
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
      //Semicolon
      case (ScalaTokenTypes.tSEMICOLON, _, parentType, _) =>
        if (BLOCK_ELEMENT_TYPES.contains(parentType) &&
          !getText(leftNode.getTreeParent, fileText).contains("\n")) COMMON_SPACING
        else IMPORT_BETWEEN_SPACING
      case (_, ScalaTokenTypes.tSEMICOLON, _, _) =>
        NO_SPACING
      //Imports
      case (ScalaElementType.IMPORT_STMT, ScalaElementType.IMPORT_STMT, _, _) => IMPORT_BETWEEN_SPACING
      case (ScalaElementType.IMPORT_STMT, _, ScalaElementType.FILE, _) => DOUBLE_LINE
      case (ScalaElementType.IMPORT_STMT, _, ScalaElementType.PACKAGING, _) => DOUBLE_LINE
      case (ScalaElementType.IMPORT_STMT, _, _, _) => IMPORT_BETWEEN_SPACING
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
      case (ScalaElementType.CASE_CLAUSE, _, _, _) => IMPORT_BETWEEN_SPACING
      case (_, ScalaElementType.CASE_CLAUSE, _, _) => IMPORT_BETWEEN_SPACING
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
      case _ =>
        COMMON_SPACING
    }
  }
}
