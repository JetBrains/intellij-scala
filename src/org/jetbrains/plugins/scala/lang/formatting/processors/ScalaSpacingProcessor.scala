package org.jetbrains.plugins.scala
package lang
package formatting
package processors

import com.intellij.formatting.Spacing
import com.intellij.lang.ASTNode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml._
import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenTypes, ScalaTokenTypesEx}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportSelectors, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import org.jetbrains.plugins.scala.util.MultilineStringUtil

import scala.annotation.tailrec

object ScalaSpacingProcessor extends ScalaTokenTypes {
  private val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.lang.formatting.processors.ScalaSpacingProcessor")

  val BLOCK_ELEMENT_TYPES = {
    import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes._
    TokenSet.create(BLOCK_EXPR, TEMPLATE_BODY, PACKAGING, TRY_BLOCK, MATCH_STMT, CATCH_BLOCK)
  }

  private def getText(node: ASTNode, fileText: String): String = {
    node.getTextRange.substring(fileText)
  }

  private def nextNotWithspace(elem: PsiElement): PsiElement = {
    var next = elem.getNextSibling
    while (next != null && (next.isInstanceOf[PsiWhiteSpace] ||
            next.getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE)) next = next.getNextSibling
    next
  }

  private def prevNotWithspace(elem: PsiElement): PsiElement = {
    var prev = elem.getPrevSibling
    while (prev != null && (prev.isInstanceOf[PsiWhiteSpace] ||
            prev.getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE)) prev = prev.getPrevSibling
    prev
  }

  private def spacesToPreventNewIds(left: ScalaBlock, right: ScalaBlock, fileText: String, textRange: TextRange): Integer = {
    if (ScalaTokenTypes.XML_ELEMENTS.contains(left.getNode.getElementType) ||
        ScalaTokenTypes.XML_ELEMENTS.contains(right.getNode.getElementType)) return 0
    @tailrec
    def dfsChildren(currentNode: ASTNode, getChildren: ASTNode => List[ASTNode]): ASTNode = {
      getChildren(currentNode) find (_.getTextLength > 0) match {
        case Some(next) => dfsChildren(next, getChildren)
        case None => currentNode
      }
    }
    val leftElement = dfsChildren(left.myLastNode.getOrElse(left.getNode), _.getChildren(null).toList.reverse)
    val rightElement = dfsChildren(right.getNode, _.getChildren(null).toList)
    val concatString = if (textRange.contains(rightElement.getTextRange) && textRange.contains(leftElement.getTextRange)) {
      leftElement.getTextRange.substring(fileText) + rightElement.getTextRange.substring(fileText)
    } else return 0
    if (ScalaNamesUtil.isIdentifier(concatString) || ScalaNamesUtil.isKeyword(concatString)) 1 else 0
  }

  def getSpacing(left: ScalaBlock, right: ScalaBlock): Spacing = {
    val settings = right.getCommonSettings
    val keepBlankLinesInCode = settings.KEEP_BLANK_LINES_IN_CODE
    val keepLineBreaks = settings.KEEP_LINE_BREAKS
    val keepBlankLinesInDeclarations = settings.KEEP_BLANK_LINES_IN_DECLARATIONS
    val keepBlankLinesBeforeRBrace = settings.KEEP_BLANK_LINES_BEFORE_RBRACE
    def getSpacing(x: Int, y: Int, z: Int) = {
      if (keepLineBreaks) Spacing.createSpacing(y, y, z, true, x)
      else Spacing.createSpacing(y, y, z, false, 0)
    }
    if (left == null) {
      return getSpacing(keepBlankLinesInCode, 0, 0) //todo:
    }
    val scalaSettings: ScalaCodeStyleSettings =
      left.getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    def getDependentLFSpacing(x: Int, y: Int, range: TextRange) = {
      if (keepLineBreaks) Spacing.createDependentLFSpacing(y, y, range, true, x)
      else Spacing.createDependentLFSpacing(y, y, range, false, 0)
    }
    val leftNode = left.getNode
    val rightNode = right.getNode
    val fileText = leftNode.getPsi.getContainingFile.getText

    //new formatter spacing
    val leftElementType = leftNode.getElementType
    val rightElementType = rightNode.getElementType
    val leftPsi = leftNode.getPsi
    val rightPsi = rightNode.getPsi
    val fileTextRange = new TextRange(0, fileText.length())

    /**
     * This is not nodes text! This is blocks text, which can be different from node.
     */
    val (leftString, rightString) =
      if (!fileTextRange.contains(left.getTextRange) ||
        !fileTextRange.contains(right.getTextRange)) {
        LOG.error("File text: \n%s\n\nDoesn't contains nodes:\n(%s, %s)".
          format(fileText, leftPsi.getText, rightPsi.getText))
        (leftPsi.getText, rightPsi.getText)
      } else (left.getTextRange.substring(fileText),
            right.getTextRange.substring(fileText))

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
    val COMMON_SPACING = Spacing.createSpacing(1, 1, 0, true, 100)
    val IMPORT_BETWEEN_SPACING = Spacing.createSpacing(0, 0, 1, true, 100)
    val IMPORT_OTHER_SPACING = Spacing.createSpacing(0, 0, 2, true, 100)

    import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
    if ((leftPsi.isInstanceOf[PsiComment] || leftPsi.isInstanceOf[PsiDocComment]) &&
            (rightPsi.isInstanceOf[PsiComment] || rightPsi.isInstanceOf[PsiDocComment])) {
      return ON_NEW_LINE
    }

    //ScalaDocs
    def docCommentOf(node: ASTNode) = node.getPsi.parentsInFile.findByType(classOf[ScDocComment]).getOrElse {
      throw new RuntimeException("Unable to find parent doc comment")
    }

    (leftNode.getElementType, rightNode.getElementType,
      leftNode.getTreeParent.getElementType, rightNode.getTreeParent.getElementType) match {
      case (_, ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS, _, _) => return NO_SPACING_WITH_NEWLINE
      case (_, ScalaDocTokenType.DOC_COMMENT_END, _, _) =>
        return if (docCommentOf(rightNode).version == 1) {
          NO_SPACING_WITH_NEWLINE
        } else {
          if (leftString(leftString.length() - 1) != ' ') WITH_SPACING else WITHOUT_SPACING
        }
      case (ScalaDocTokenType.DOC_COMMENT_START, _, _, _) =>
        return if (docCommentOf(leftNode).version == 1) {
          NO_SPACING_WITH_NEWLINE
        } else {
          if (getText(rightNode, fileText)(0) != ' ') WITH_SPACING else WITHOUT_SPACING
        }
      case (ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS, _, _, _) =>
        if (getText(rightNode, fileText).apply(0) != ' ') return WITH_SPACING
        else return WITHOUT_SPACING
      case (ScalaDocTokenType.DOC_TAG_NAME, ScalaDocTokenType.DOC_TAG_VALUE_TOKEN, _, _) => return WITH_SPACING
      case (_, x, _, _) if ScalaDocTokenType.ALL_SCALADOC_TOKENS.contains(x) => return Spacing.getReadOnlySpacing
      case (x, _, _, _) if ScalaDocTokenType.ALL_SCALADOC_TOKENS.contains(x) => return Spacing.getReadOnlySpacing
      case _ =>
    }

    //Xml
    (leftNode.getElementType, rightNode.getElementType,
            leftNode.getTreeParent.getElementType, rightNode.getTreeParent.getElementType) match {
      case (ScalaElementTypes.XML_START_TAG, ScalaElementTypes.XML_END_TAG, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) return Spacing.getReadOnlySpacing
        return WITHOUT_SPACING
      case (ScalaElementTypes.XML_START_TAG, XmlTokenType.XML_DATA_CHARACTERS, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) return Spacing.getReadOnlySpacing
        return WITHOUT_SPACING
      case (XmlTokenType.XML_DATA_CHARACTERS, ScalaElementTypes.XML_END_TAG, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) return Spacing.getReadOnlySpacing
        return WITHOUT_SPACING
      case (ScalaElementTypes.XML_START_TAG, _, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) return Spacing.getReadOnlySpacing
        return ON_NEW_LINE
      case (_, ScalaElementTypes.XML_END_TAG, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) return Spacing.getReadOnlySpacing
        return ON_NEW_LINE
      case (XmlTokenType.XML_DATA_CHARACTERS, XmlTokenType.XML_DATA_CHARACTERS, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) return Spacing.getReadOnlySpacing
        return WITH_SPACING
      case (XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, XmlTokenType.XML_CHAR_ENTITY_REF, _, _) =>
        return Spacing.getReadOnlySpacing
      case (XmlTokenType.XML_CHAR_ENTITY_REF, XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, _, _) =>
        return Spacing.getReadOnlySpacing
      case (XmlTokenType.XML_DATA_CHARACTERS, XmlTokenType.XML_CDATA_END, _, _) =>
        return Spacing.getReadOnlySpacing
      case (XmlTokenType.XML_DATA_CHARACTERS, _, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) return Spacing.getReadOnlySpacing
        return ON_NEW_LINE
      case (XmlTokenType.XML_CDATA_START, XmlTokenType.XML_DATA_CHARACTERS, _, _) =>
        return Spacing.getReadOnlySpacing
      case (_, XmlTokenType.XML_DATA_CHARACTERS, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) return Spacing.getReadOnlySpacing
        return ON_NEW_LINE
      case (ScalaElementTypes.XML_EMPTY_TAG, ScalaElementTypes.XML_EMPTY_TAG, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) return Spacing.getReadOnlySpacing
        return ON_NEW_LINE
      case (_, ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START | ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_END, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) return Spacing.getReadOnlySpacing
        return NO_SPACING
      case (XmlTokenType.XML_START_TAG_START | XmlTokenType.XML_END_TAG_START |
              XmlTokenType.XML_CDATA_START | XmlTokenType.XML_PI_START, _, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) return Spacing.getReadOnlySpacing
        return NO_SPACING
      case (_, XmlTokenType.XML_TAG_END | XmlTokenType.XML_EMPTY_ELEMENT_END |
              XmlTokenType.XML_CDATA_END | XmlTokenType.XML_PI_END, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) return Spacing.getReadOnlySpacing
        return NO_SPACING
      case (XmlTokenType.XML_NAME, ScalaElementTypes.XML_ATTRIBUTE, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) return Spacing.getReadOnlySpacing
        return COMMON_SPACING
      case (XmlTokenType.XML_NAME, XmlTokenType.XML_EQ, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) return Spacing.getReadOnlySpacing
        return NO_SPACING
      case (XmlTokenType.XML_EQ, XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER |
              ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) return Spacing.getReadOnlySpacing
        return NO_SPACING
      case (XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER, XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) return Spacing.getReadOnlySpacing
        return NO_SPACING
      case (XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) return Spacing.getReadOnlySpacing
        return NO_SPACING
      case (ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START | ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_END, _, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) return Spacing.getReadOnlySpacing
        return NO_SPACING
      case (_, XmlTokenType.XML_DATA_CHARACTERS | XmlTokenType.XML_COMMENT_END
              | XmlTokenType.XML_COMMENT_CHARACTERS, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) return Spacing.getReadOnlySpacing
        return NO_SPACING
      case (XmlTokenType.XML_DATA_CHARACTERS | XmlTokenType.XML_COMMENT_START
              | XmlTokenType.XML_COMMENT_CHARACTERS, _, _, _) =>
        if (scalaSettings.KEEP_XML_FORMATTING) return Spacing.getReadOnlySpacing
        return NO_SPACING
      case (el1, el2, _, _) if scalaSettings.KEEP_XML_FORMATTING &&
        (XML_ELEMENTS.contains(el1) || XML_ELEMENTS.contains(el2)) => return Spacing.getReadOnlySpacing
      case _ =>
    }

    if (leftElementType == tLPARENTHESIS &&
            (leftPsi.getParent.isInstanceOf[ScParenthesisedExpr] ||
                    leftPsi.getParent.isInstanceOf[ScParameterizedTypeElement] ||
                    leftPsi.getParent.isInstanceOf[ScParenthesisedPattern])) {
      if (settings.PARENTHESES_EXPRESSION_LPAREN_WRAP) {
        if (settings.SPACE_WITHIN_PARENTHESES) return WITH_SPACING_DEPENDENT(leftPsi.getParent.getTextRange)
        else return WITHOUT_SPACING_DEPENDENT(leftPsi.getParent.getTextRange)
      }
      else if (settings.SPACE_WITHIN_PARENTHESES) return WITH_SPACING
      else return WITHOUT_SPACING
    }

    //for interpolated strings
    if (rightElementType == tINTERPOLATED_STRING_ESCAPE) return Spacing.getReadOnlySpacing
    if (Set(tINTERPOLATED_STRING, tINTERPOLATED_MULTILINE_STRING).contains(rightElementType)) return WITHOUT_SPACING
    if (Set(leftElementType, rightElementType).contains(tINTERPOLATED_STRING_INJECTION) ||
      rightElementType == tINTERPOLATED_STRING_END) return Spacing.getReadOnlySpacing
    if (Option(leftNode.getTreeParent.getTreePrev).exists(_.getElementType == tINTERPOLATED_STRING_ID)) {
      return Spacing.getReadOnlySpacing
    }

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
        case (true, true) =>
          return if (rightPsi.getPrevSibling != null && rightPsi.getPrevSibling.getText.contains("\n")) ON_NEW_LINE else WITH_SPACING
        case (true, false) => return ON_NEW_LINE
        case (false, false) => return WITH_SPACING_NO_KEEP
        case (false, true) => return Spacing.createDependentLFSpacing(1, 1, rightPsi.getParent.getTextRange, true, 1)
      }
    }

    leftPsi match {
      case l: ScLiteral if l.isMultiLineString && rightNode == leftNode =>
        val marginChar = "" + MultilineStringUtil.getMarginChar(leftPsi)

        if (leftString == marginChar && rightString != "\"\"\"" && rightString != marginChar) {
          return Spacing.getReadOnlySpacing
        }
      case _ =>
    }

    if (rightElementType == tRPARENTHESIS &&
            (rightPsi.getParent.isInstanceOf[ScParenthesisedExpr] ||
                    rightPsi.getParent.isInstanceOf[ScParameterizedTypeElement] ||
                    rightPsi.getParent.isInstanceOf[ScParenthesisedPattern])) {
      if (settings.PARENTHESES_EXPRESSION_RPAREN_WRAP) {
        if (settings.SPACE_WITHIN_PARENTHESES) return WITH_SPACING_DEPENDENT(rightPsi.getParent.getTextRange)
        else return WITHOUT_SPACING_DEPENDENT(rightPsi.getParent.getTextRange)
      }
      else if (settings.SPACE_WITHIN_PARENTHESES) return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (leftElementType == tIDENTIFIER &&
            rightPsi.isInstanceOf[ScArgumentExprList] && !getText(rightNode, fileText).trim.startsWith("{")) {
      if (settings.SPACE_BEFORE_METHOD_CALL_PARENTHESES) return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (leftElementType == tLPARENTHESIS && (leftPsi.getParent.isInstanceOf[ScArgumentExprList] ||
            leftPsi.getParent.isInstanceOf[ScPatternArgumentList])) {
      if (settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE) {
        if (settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES) return WITH_SPACING_DEPENDENT(leftPsi.getParent.getTextRange)
        else return WITHOUT_SPACING_DEPENDENT(leftPsi.getParent.getTextRange)
      } else if (settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES) return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (rightElementType == tRPARENTHESIS && (rightPsi.getParent.isInstanceOf[ScArgumentExprList] ||
            rightPsi.getParent.isInstanceOf[ScPatternArgumentList])) {
      if (settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE) {
        if (settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES)
          return WITH_SPACING_DEPENDENT(rightPsi.getParent.getTextRange)
        else return WITHOUT_SPACING_DEPENDENT(rightPsi.getParent.getTextRange)
      } else if (settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES) return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (leftElementType == tLPARENTHESIS && leftPsi.getParent.isInstanceOf[ScParameterClause]) {
      if (settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE) {
        if (settings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING_DEPENDENT(leftPsi.getParent.getTextRange)
        else return WITHOUT_SPACING_DEPENDENT(leftPsi.getParent.getTextRange)
      } else if (settings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (rightElementType == tRPARENTHESIS && rightPsi.getParent.isInstanceOf[ScParameterClause]) {
      if (settings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE) {
        if (settings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING_DEPENDENT(rightPsi.getParent.getTextRange)
        else return WITHOUT_SPACING_DEPENDENT(rightPsi.getParent.getTextRange)
      } else if (settings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING
      else return WITHOUT_SPACING
    }
    //todo: spacing for early definitions
    if (getText(rightNode, fileText).trim.startsWith("{")) {
      if (rightPsi.isInstanceOf[ScImportSelectors]) {
        return WITHOUT_SPACING
      }
      if (leftPsi.getParent.isInstanceOf[ScParenthesisedTypeElement]) {
        return WITHOUT_SPACING
      }
      if (rightPsi.isInstanceOf[ScExtendsBlock] || rightPsi.isInstanceOf[ScEarlyDefinitions] || rightPsi.isInstanceOf[ScTemplateBody]) {
        val extendsBlock = rightPsi match {
          case e: ScExtendsBlock => e
          case t: ScEarlyDefinitions => t.getParent
          case t: ScTemplateBody => t.getParent
        }
        settings.CLASS_BRACE_STYLE match {
          case CommonCodeStyleSettings.NEXT_LINE => return ON_NEW_LINE
          case CommonCodeStyleSettings.NEXT_LINE_SHIFTED => return ON_NEW_LINE
          case CommonCodeStyleSettings.NEXT_LINE_SHIFTED2 => return ON_NEW_LINE
          case CommonCodeStyleSettings.END_OF_LINE =>
            val leftLineComment = leftNode.getElementType == tLINE_COMMENT
            if (settings.SPACE_BEFORE_CLASS_LBRACE) return if (leftLineComment) WITH_SPACING else WITH_SPACING_NO_KEEP
            else return if (leftLineComment) WITHOUT_SPACING else WITHOUT_SPACING_NO_KEEP
          case CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED =>
            val startOffset = extendsBlock.getParent.getParent match {
              case b: ScTypeDefinition => b.nameId.getTextRange.getStartOffset
              case b: ScTemplateDefinition => b.nameId.getTextRange.getStartOffset
              case b => b.getTextRange.getStartOffset
            }
            val range = new TextRange(startOffset, rightPsi.getTextRange.getStartOffset)
            if (settings.SPACE_BEFORE_CLASS_LBRACE) return WITH_SPACING_DEPENDENT(range)
            else return WITHOUT_SPACING_DEPENDENT(range)
        }
      } else {
        rightPsi.getParent match {
          case fun: ScFunction =>
            settings.METHOD_BRACE_STYLE match {
              case CommonCodeStyleSettings.NEXT_LINE => return ON_NEW_LINE
              case CommonCodeStyleSettings.NEXT_LINE_SHIFTED => return ON_NEW_LINE
              case CommonCodeStyleSettings.NEXT_LINE_SHIFTED2 => return ON_NEW_LINE
              case CommonCodeStyleSettings.END_OF_LINE =>
                if (settings.SPACE_BEFORE_METHOD_LBRACE) return WITH_SPACING_NO_KEEP
                else return WITHOUT_SPACING_NO_KEEP
              case CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED =>
                val startOffset = fun.nameId.getTextRange.getStartOffset
                val range = new TextRange(startOffset, rightPsi.getTextRange.getStartOffset)
                if (settings.SPACE_BEFORE_METHOD_LBRACE) return WITH_SPACING_DEPENDENT(range)
                else return WITHOUT_SPACING_DEPENDENT(range)
            }
          case _: ScBlock | _: ScEarlyDefinitions | _: ScTemplateBody if !rightPsi.getParent.isInstanceOf[ScTryBlock] => return ON_NEW_LINE
          case parent =>
            settings.BRACE_STYLE match {
              case CommonCodeStyleSettings.NEXT_LINE => return ON_NEW_LINE
              case CommonCodeStyleSettings.NEXT_LINE_SHIFTED => return ON_NEW_LINE
              case CommonCodeStyleSettings.NEXT_LINE_SHIFTED2 => return ON_NEW_LINE
              case CommonCodeStyleSettings.END_OF_LINE =>
                return WITH_SPACING_NO_KEEP //todo: spacing settings
              case CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED =>
                val startOffset = parent.getTextRange.getStartOffset
                val range = new TextRange(startOffset, rightPsi.getTextRange.getStartOffset)
                return WITH_SPACING_DEPENDENT(range) //todo: spacing settings
            }
        }
      }
    }


    if (leftPsi.isInstanceOf[ScStableCodeReferenceElement] && !rightPsi.isInstanceOf[ScPackaging]) {
      leftPsi.getParent match {
        case p: ScPackaging if p.reference == Some(leftPsi) =>
          if (rightElementType != ScalaTokenTypes.tSEMICOLON && rightElementType != ScalaTokenTypes.tLBRACE) {
            return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AFTER_PACKAGE + 1, keepLineBreaks,
              keepBlankLinesInCode)
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
      if (leftPsi.isInstanceOf[ScStableCodeReferenceElement] || leftElementType == tLBRACE)
        return Spacing.createSpacing(0, 0, 1, keepLineBreaks, keepBlankLinesInCode)
      else
        return Spacing.createSpacing(0, 0, settings.BLANK_LINES_BEFORE_PACKAGE + 1, keepLineBreaks,
          keepBlankLinesInCode)
    }

    if (leftPsi.isInstanceOf[ScImportStmt] && !rightPsi.isInstanceOf[ScImportStmt]) {
      if (rightElementType != ScalaTokenTypes.tSEMICOLON) {
        leftPsi.getParent match {
          case _: ScEarlyDefinitions | _: ScTemplateBody | _: ScalaFile | _: ScPackaging =>
            return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AFTER_IMPORTS + 1, keepLineBreaks,
              keepBlankLinesInCode)
          case _ =>
        }
      } else if (settings.SPACE_BEFORE_SEMICOLON) return WITH_SPACING
      else return WITHOUT_SPACING
    }

    if (rightPsi.isInstanceOf[ScImportStmt] && !leftPsi.isInstanceOf[ScImportStmt]) {
      if (leftElementType != ScalaTokenTypes.tSEMICOLON || !prevNotWithspace(leftPsi).isInstanceOf[ScImportStmt]) {
        rightPsi.getParent match {
          case _: ScEarlyDefinitions | _: ScTemplateBody | _: ScalaFile | _: ScPackaging =>
            return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AFTER_IMPORTS + 1, keepLineBreaks,
              keepBlankLinesInCode)
          case _ =>
        }
      }
    }

    if (leftPsi.isInstanceOf[ScImportStmt] || rightPsi.isInstanceOf[ScImportStmt]) {
      return Spacing.createSpacing(0, 0, 1, keepLineBreaks, keepBlankLinesInDeclarations)
    }


    if (leftPsi.isInstanceOf[ScTypeDefinition]) {
      if (rightElementType != ScalaTokenTypes.tSEMICOLON) {
        leftPsi.getParent match {
          case _: ScEarlyDefinitions | _: ScTemplateBody | _: ScalaFile | _: ScPackaging =>
            return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AROUND_CLASS + 1, keepLineBreaks,
              keepBlankLinesInDeclarations)
          case _ =>
        }
      }
    }

    if (rightPsi.isInstanceOf[PsiComment] || rightPsi.isInstanceOf[PsiDocComment]) {
      var pseudoRightPsi = nextNotWithspace(rightPsi)
      while (pseudoRightPsi != null &&
              (pseudoRightPsi.isInstanceOf[PsiComment] || pseudoRightPsi.isInstanceOf[PsiDocComment])) {
        pseudoRightPsi = nextNotWithspace(pseudoRightPsi)
      }
      if (pseudoRightPsi.isInstanceOf[ScTypeDefinition]) {
        pseudoRightPsi.getParent match {
          case _: ScEarlyDefinitions | _: ScTemplateBody | _: ScalaFile | _: ScPackaging =>
            return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AROUND_CLASS + 1, keepLineBreaks,
              keepBlankLinesInDeclarations)
          case _ =>
        }
      }
    }

    if (rightPsi.isInstanceOf[ScTypeDefinition]) {
      if (leftPsi.isInstanceOf[PsiComment] || leftPsi.isInstanceOf[PsiDocComment]) {
        return ON_NEW_LINE
      }
      rightPsi.getParent match {
        case _: ScEarlyDefinitions | _: ScTemplateBody | _: ScalaFile | _: ScPackaging =>
          return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AROUND_CLASS + 1, keepLineBreaks,
            keepBlankLinesInDeclarations)
        case _ =>
      }
    }

    if (rightNode.getElementType == ScalaTokenTypes.tRBRACE) {
      rightNode.getTreeParent.getPsi match {
        case block@(_: ScEarlyDefinitions | _: ScTemplateBody | _: ScPackaging | _: ScBlockExpr | _: ScMatchStmt |
                _: ScTryBlock | _: ScCatchBlock) =>

          val oneLineNonEmpty = leftString != "{" && !block.getText.contains('\n')
          val spaceInsideOneLineMethod = scalaSettings.INSERT_WHITESPACES_IN_SIMPLE_ONE_LINE_METHOD &&
            rightNode.getTreeParent.getTreeParent != null && rightNode.getTreeParent.getTreeParent.getPsi.isInstanceOf[ScFunction]
          val spaceInsideClosure = scalaSettings.SPACE_INSIDE_CLOSURE_BRACES && (leftNode.getElementType match {
            case ScalaElementTypes.FUNCTION_EXPR => true
            case ScalaElementTypes.CASE_CLAUSES => block.getParent.isInstanceOf[ScArgumentExprList] ||
                block.getParent.isInstanceOf[ScInfixExpr]
            case _ =>
              scalaSettings.KEEP_ONE_LINE_LAMBDAS_IN_ARG_LIST &&
                  (leftPsi.isInstanceOf[ScFunctionExpr] || block.isInstanceOf[ScBlockExpr] || leftPsi.isInstanceOf[ScCaseClauses])
          })
          val needsSpace = (oneLineNonEmpty && (spaceInsideOneLineMethod || spaceInsideClosure ||
                  scalaSettings.SPACES_IN_ONE_LINE_BLOCKS)) ||
                  leftPsi.isInstanceOf[PsiComment] && scalaSettings.KEEP_ONE_LINE_LAMBDAS_IN_ARG_LIST
          val spaces = if (needsSpace) 1 else 0


          return Spacing.createDependentLFSpacing(spaces, spaces, block.getTextRange, keepLineBreaks, keepBlankLinesBeforeRBrace)
        case _: ScImportSelectors =>
          return if (scalaSettings.SPACES_IN_IMPORTS) WITH_SPACING else WITHOUT_SPACING
        case _ => return Spacing.createSpacing(0, 0, 0, keepLineBreaks, keepBlankLinesBeforeRBrace)
      }
    }

    if (leftNode.getElementType == ScalaTokenTypes.tLBRACE) {
      if (!scalaSettings.PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE) {
        val b = leftNode.getTreeParent.getPsi
        val spaceInsideOneLineBlock = scalaSettings.SPACES_IN_ONE_LINE_BLOCKS && !b.getText.contains('\n')
        val spacing = if (scalaSettings.SPACE_INSIDE_CLOSURE_BRACES ||
            spaceInsideOneLineBlock) WITH_SPACING else WITHOUT_SPACING
        rightNode.getElementType match {
          case ScalaElementTypes.FUNCTION_EXPR => return spacing
          case ScalaElementTypes.CASE_CLAUSES =>
            if (b.getParent.isInstanceOf[ScArgumentExprList] || b.getParent.isInstanceOf[ScInfixExpr]) return spacing
          case _ =>
        }
      }
      leftNode.getTreeParent.getPsi match {
        case b: ScTemplateBody if rightPsi.isInstanceOf[ScSelfTypeElement] =>
          if (scalaSettings.PLACE_SELF_TYPE_ON_NEW_LINE) {
            return ON_NEW_LINE
          } else return WITHOUT_SPACING_NO_KEEP //todo: spacing setting
        case b @ (_: ScEarlyDefinitions | _: ScTemplateBody) =>
          if (settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE && !getText(b.getNode, fileText).contains('\n')) {
            return Spacing.createDependentLFSpacing(0, 0, b.getTextRange, keepLineBreaks,
              keepBlankLinesBeforeRBrace)
          }
          val c = PsiTreeUtil.getParentOfType(b, classOf[ScTemplateDefinition])
          val setting = if (c.isInstanceOf[ScTypeDefinition]) settings.BLANK_LINES_AFTER_CLASS_HEADER
          else settings.BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER
          return Spacing.createSpacing(0, 0, setting + 1, keepLineBreaks, keepBlankLinesInDeclarations)
        case b: ScBlockExpr if b.getParent.isInstanceOf[ScFunction] =>
          if (settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE && !getText(b.getNode, fileText).contains('\n')) {
            val spaces = if (scalaSettings.INSERT_WHITESPACES_IN_SIMPLE_ONE_LINE_METHOD) 1 else 0
            return Spacing.createDependentLFSpacing(spaces, spaces, b.getTextRange, keepLineBreaks,
              keepBlankLinesBeforeRBrace)
          }
          return Spacing.createSpacing(0, 0, settings.BLANK_LINES_BEFORE_METHOD_BODY + 1, keepLineBreaks, keepBlankLinesInDeclarations)
        case b: ScBlockExpr if scalaSettings.KEEP_ONE_LINE_LAMBDAS_IN_ARG_LIST &&
          !b.getText.contains('\n') && (rightPsi.isInstanceOf[ScCaseClauses] && b.getParent != null &&
          b.getParent.isInstanceOf[ScArgumentExprList] || rightPsi.isInstanceOf[ScFunctionExpr]) =>
          return Spacing.createDependentLFSpacing(1, 1, b.getTextRange, keepLineBreaks, keepBlankLinesBeforeRBrace)
        case b: ScBlockExpr if scalaSettings.SPACE_INSIDE_CLOSURE_BRACES && !b.getText.contains('\n') &&
          scalaSettings.KEEP_ONE_LINE_LAMBDAS_IN_ARG_LIST &&
          (b.getParent.isInstanceOf[ScArgumentExprList] || b.getParent.isInstanceOf[ScInfixExpr]) => return WITH_SPACING
        case block@(_: ScPackaging | _: ScBlockExpr | _: ScMatchStmt |
                    _: ScTryBlock | _: ScCatchBlock) =>
          val prev = block.getPrevSibling
          if (settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE || prev != null &&
                  prev.getNode.getElementType == tINTERPOLATED_STRING_INJECTION) {
            val spaces = if (scalaSettings.SPACES_IN_ONE_LINE_BLOCKS) 1 else 0
            return Spacing.createDependentLFSpacing(spaces, spaces, block.getTextRange, keepLineBreaks,
              keepBlankLinesBeforeRBrace)
          } else {
            return ON_NEW_LINE
          }
        case _: ScImportSelectors =>
          return if (scalaSettings.SPACES_IN_IMPORTS) WITH_SPACING else WITHOUT_SPACING
        case _ => return Spacing.createSpacing(0, 0, 0, keepLineBreaks, keepBlankLinesBeforeRBrace)
      }
    }

    if (leftPsi.isInstanceOf[ScSelfTypeElement]) {
      val c = PsiTreeUtil.getParentOfType(leftPsi, classOf[ScTemplateDefinition])
      val setting = if (c.isInstanceOf[ScTypeDefinition]) settings.BLANK_LINES_AFTER_CLASS_HEADER
      else settings.BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER
      return Spacing.createSpacing(0, 0, setting + 1, keepLineBreaks, keepBlankLinesInDeclarations)
    }

    if (leftPsi.isInstanceOf[ScFunction] || leftPsi.isInstanceOf[ScValue] || leftPsi.isInstanceOf[ScVariable] || leftPsi.isInstanceOf[ScTypeAlias]) {
      if (rightElementType != tSEMICOLON) {
        leftPsi.getParent match {
          case b @ (_: ScEarlyDefinitions | _: ScTemplateBody) =>
            val p = PsiTreeUtil.getParentOfType(b, classOf[ScTemplateDefinition])
            val setting = leftPsi match {
              case _: ScFunction if p.isInstanceOf[ScTrait] => settings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE
              case _: ScFunction => settings.BLANK_LINES_AROUND_METHOD
              case _ =>
                rightPsi match {
                case _: ScFunction if p.isInstanceOf[ScTrait] => settings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE
                case _: ScFunction => settings.BLANK_LINES_AROUND_METHOD
                case _ if p.isInstanceOf[ScTrait] => settings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE
                case _ => settings.BLANK_LINES_AROUND_FIELD
              }
            }
            if (rightPsi.isInstanceOf[PsiComment] && !fileText.
              substring(leftPsi.getTextRange.getEndOffset, rightPsi.getTextRange.getEndOffset).contains("\n"))
              return COMMON_SPACING
            else
              return Spacing.createSpacing(0, 0, setting + 1, keepLineBreaks, keepBlankLinesInDeclarations)
          case _ =>
        }
      }
    }

    if (rightPsi.isInstanceOf[PsiComment] || rightPsi.isInstanceOf[PsiDocComment]) {
      var pseudoRightPsi = nextNotWithspace(rightPsi)
      while (pseudoRightPsi != null &&
              (pseudoRightPsi.isInstanceOf[PsiComment] || pseudoRightPsi.isInstanceOf[PsiDocComment])) {
        pseudoRightPsi = nextNotWithspace(pseudoRightPsi)
      }
      if (pseudoRightPsi.isInstanceOf[ScFunction] || pseudoRightPsi.isInstanceOf[ScValue] ||
              pseudoRightPsi.isInstanceOf[ScVariable] || pseudoRightPsi.isInstanceOf[ScTypeAlias]) {
        pseudoRightPsi.getParent match {
          case b @ (_: ScEarlyDefinitions | _: ScTemplateBody) =>
            val p = PsiTreeUtil.getParentOfType(b, classOf[ScTemplateDefinition])
            val setting = (pseudoRightPsi, p) match {
                case (_: ScFunction, _: ScTrait) => settings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE
                case (_: ScFunction, _) => settings.BLANK_LINES_AROUND_METHOD
                case (_, _: ScTrait) => settings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE
                case _ => settings.BLANK_LINES_AROUND_FIELD
              }
            return Spacing.createSpacing(0, 0, setting + 1, keepLineBreaks, keepBlankLinesInDeclarations)
          case _ =>
        }
      }
    }

    if (rightPsi.isInstanceOf[ScFunction] || rightPsi.isInstanceOf[ScValue] || rightPsi.isInstanceOf[ScVariable] || rightPsi.isInstanceOf[ScTypeAlias]) {
      if (leftPsi.isInstanceOf[PsiComment] || leftPsi.isInstanceOf[PsiDocComment]) {
        return ON_NEW_LINE
      }
      rightPsi.getParent match {
        case b @ (_: ScEarlyDefinitions | _: ScTemplateBody) =>
          val p = PsiTreeUtil.getParentOfType(b, classOf[ScTemplateDefinition])
          val setting = (rightPsi, p) match {
            case (_: ScFunction, _: ScTrait) => settings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE
            case (_: ScFunction, _) => settings.BLANK_LINES_AROUND_METHOD
            case (_, _: ScTrait) => settings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE
            case _ => settings.BLANK_LINES_AROUND_FIELD
          }
          return Spacing.createSpacing(0, 0, setting + 1, keepLineBreaks, keepBlankLinesInDeclarations)
        case _ =>
      }
    }

    //special else if treatment
    if (leftNode.getElementType == ScalaTokenTypes.kELSE && rightNode.getPsi.isInstanceOf[ScIfStmt]) {
      if (settings.SPECIAL_ELSE_IF_TREATMENT) return WITH_SPACING
      else return ON_NEW_LINE
    }
    if (rightNode.getElementType == ScalaTokenTypes.kELSE && right.myLastNode != null) {
      var lastNode = left.myLastNode
      while (lastNode != null && (ScalaPsiUtil.isLineTerminator(lastNode.getPsi) ||
              lastNode.getPsi.isInstanceOf[PsiWhiteSpace])) lastNode = lastNode.getTreePrev
      if (lastNode == null) return WITH_SPACING_DEPENDENT(rightNode.getTreeParent.getTextRange)
      else if (getText(lastNode, fileText).endsWith("}")) {
        if (settings.ELSE_ON_NEW_LINE) return ON_NEW_LINE
        else return WITH_SPACING
      } else return WITH_SPACING_DEPENDENT(rightNode.getTreeParent.getTextRange)
    }

    if (leftElementType == ScalaElementTypes.MODIFIERS) {
      if (rightPsi.isInstanceOf[ScParameters]) {
        if (scalaSettings.SPACE_AFTER_MODIFIERS_CONSTRUCTOR) return WITH_SPACING
        else return WITHOUT_SPACING
      }
      if (settings.MODIFIER_LIST_WRAP) return WITH_SPACING_DEPENDENT(leftNode.getTreeParent.getTextRange)
      else return WITH_SPACING
    }

    if (rightPsi.isInstanceOf[ScCatchBlock]) {
      if (settings.CATCH_ON_NEW_LINE) return ON_NEW_LINE
      else return WITH_SPACING
    }

    if (rightPsi.isInstanceOf[ScFinallyBlock]) {
      if (settings.FINALLY_ON_NEW_LINE) return ON_NEW_LINE
      else return WITH_SPACING
    }

    if (rightElementType == kWHILE) {
      if (settings.WHILE_ON_NEW_LINE) return WITH_SPACING_DEPENDENT(rightPsi.getParent.getTextRange)
      else return WITH_SPACING
    }

    if (rightElementType == tAT &&  rightNode.getTreeParent != null &&
        rightNode.getTreeParent.getElementType == ScalaElementTypes.NAMING_PATTERN) {
      return if (scalaSettings.SPACES_AROUND_AT_IN_PATTERNS) WITH_SPACING else WITHOUT_SPACING
    }

    if (leftElementType == tAT && leftNode.getTreeParent != null &&
        leftNode.getTreeParent.getElementType == ScalaElementTypes.NAMING_PATTERN) {
      return if (scalaSettings.SPACES_AROUND_AT_IN_PATTERNS) WITH_SPACING else WITHOUT_SPACING
    }

    if (leftElementType == ScalaDocTokenType.DOC_COMMENT_BAD_CHARACTER ||
        rightElementType == ScalaDocTokenType.DOC_COMMENT_BAD_CHARACTER) {
      //FIXME: this is a quick hack to stop method signature in scalaDoc from getting disrupted. (#SCL-4280)
      //actually the DOC_COMMENT_BAD_CHARACTER elements seem out of place in here
      return Spacing.getReadOnlySpacing
    }

    //old formatter spacing


    //comments processing
    if (leftNode.getPsi.isInstanceOf[ScDocComment]) return ON_NEW_LINE
    if (rightNode.getPsi.isInstanceOf[ScDocComment] && leftNode.getElementType == ScalaTokenTypes.tLBRACE) return ON_NEW_LINE
    if (rightNode.getPsi.isInstanceOf[ScDocComment]) return DOUBLE_LINE
    if (rightNode.getPsi.isInstanceOf[PsiComment] || leftNode.getPsi.isInstanceOf[PsiComment])
      return COMMON_SPACING
    //; : . and , processing
    if (rightString.length > 0 && rightString(0) == '.') {
      if (rightNode.getElementType != ScalaTokenTypes.tFLOAT && !rightNode.getPsi.isInstanceOf[ScLiteral]) return WITHOUT_SPACING
    }
    if (rightString.length > 0 && rightString(0) == ',') {
      if (settings.SPACE_BEFORE_COMMA) return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (rightNode.getElementType == ScalaTokenTypes.tCOLON) {
      var left = leftNode
      // For operations like
      // var Object_!= : Symbol = _
      if (scalaSettings.SPACE_BEFORE_TYPE_COLON) return WITH_SPACING  //todo:
      while (left != null && left.getLastChildNode != null) {
        left = left.getLastChildNode
      }
      val tp = PsiTreeUtil.getParentOfType(left.getPsi, classOf[ScTypeParam])
      if (tp ne null) {
          return if (tp.nameId.getNode eq left) WITHOUT_SPACING else WITH_SPACING
      }
      return if (left.getElementType == ScalaTokenTypes.tIDENTIFIER &&
        ScalaNamesUtil.isIdentifier(getText(left, fileText) + ":")) WITH_SPACING else WITHOUT_SPACING
    }
    if (rightString.length > 0 && rightString(0) == ';') {
      if (settings.SPACE_BEFORE_SEMICOLON && !rightNode.getTreeParent.getPsi.isInstanceOf[ScalaFile] &&
              rightNode.getPsi.getParent.getParent.isInstanceOf[ScForStatement]) return WITH_SPACING
      else if (!rightNode.getTreeParent.getPsi.isInstanceOf[ScalaFile] &&
              rightNode.getPsi.getParent.getParent.isInstanceOf[ScForStatement]) return WITHOUT_SPACING
    }
    if (leftString.length > 0 && leftString(leftString.length - 1) == '.') {
      return WITHOUT_SPACING
    }
    if (leftString.length > 0 && leftString(leftString.length - 1) == ',') {
      if (settings.SPACE_AFTER_COMMA) return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (leftNode.getElementType == ScalaTokenTypes.tCOLON) {
      if (scalaSettings.SPACE_AFTER_TYPE_COLON) return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (leftString.length > 0 && leftString(leftString.length - 1) == ';') {
      if (settings.SPACE_AFTER_SEMICOLON && !rightNode.getTreeParent.getPsi.isInstanceOf[ScalaFile] &&
              rightNode.getPsi.getParent.getParent.isInstanceOf[ScForStatement]) return WITH_SPACING
      else if (!rightNode.getTreeParent.getPsi.isInstanceOf[ScalaFile] &&
              rightNode.getPsi.getParent.getParent.isInstanceOf[ScForStatement]) return WITHOUT_SPACING
    }

    if (leftNode.getElementType == ScalaTokenTypes.tSEMICOLON) {
      if (getText(leftNode.getTreeParent, fileText).indexOf('\n') == -1) return WITH_SPACING
      else ON_NEW_LINE
    }

    //processing left parenthesis (if it's from right) as Java cases
    if (rightNode.getElementType == ScalaTokenTypes.tLPARENTHESIS) {
      leftNode.getElementType match {
        case ScalaTokenTypes.kIF =>
          if (settings.SPACE_BEFORE_IF_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case ScalaTokenTypes.kWHILE =>
          if (settings.SPACE_BEFORE_WHILE_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case ScalaTokenTypes.kFOR =>
          if (settings.SPACE_BEFORE_FOR_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _ =>
      }
    }
    if (rightNode.getPsi.isInstanceOf[ScParameters] &&
            leftNode.getTreeParent.getPsi.isInstanceOf[ScFunction]) {
      if (settings.SPACE_BEFORE_METHOD_PARENTHESES || (scalaSettings.SPACE_BEFORE_INFIX_LIKE_METHOD_PARENTHESES &&
              ScalaNamesUtil.isOperatorName(leftNode.getTreeParent.getPsi.asInstanceOf[ScFunction].name)) ||
              (scalaSettings.PRESERVE_SPACE_AFTER_METHOD_DECLARATION_NAME &&
                      rightNode.getTreePrev.getPsi.isInstanceOf[PsiWhiteSpace]))
        return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (rightPsi.isInstanceOf[ScArguments] &&
            (leftNode.getTreeParent.getPsi.isInstanceOf[ScMethodCall] ||
                    leftNode.getTreeParent.getPsi.isInstanceOf[ScConstructor]) ||
            rightPsi.isInstanceOf[ScArguments] && rightNode.getTreeParent.getPsi.isInstanceOf[ScSelfInvocation] &&
                    leftNode.getText == "this") {
      if (settings.SPACE_BEFORE_METHOD_CALL_PARENTHESES && !rightString.startsWith("{") &&
              (leftNode.getLastChildNode == null || !leftNode.getLastChildNode.getPsi.isInstanceOf[ScArguments]) &&
              !leftPsi.isInstanceOf[ScArguments])
        return WITH_SPACING
      else if (scalaSettings.SPACE_BEFORE_BRACE_METHOD_CALL && rightString.startsWith("{")) return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (rightNode.getTreeParent.getPsi.isInstanceOf[ScSelfInvocation] &&
            leftNode.getTreeParent.getPsi.isInstanceOf[ScSelfInvocation] && leftPsi.isInstanceOf[ScArguments] &&
            rightPsi.isInstanceOf[ScArguments]) {
      return WITHOUT_SPACING
    }
    // SCL-2601
    if ((rightNode.getPsi.isInstanceOf[ScUnitExpr] || rightNode.getPsi.isInstanceOf[ScTuple]) &&
            leftNode.getTreeParent.getPsi.isInstanceOf[ScInfixExpr]) {
      if (scalaSettings.SPACE_BEFORE_INFIX_METHOD_CALL_PARENTHESES) return WITH_SPACING
      else return WITHOUT_SPACING
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
      case _: ScPrimaryConstructor if rightString.startsWith("(") =>
        if (settings.SPACE_BEFORE_METHOD_PARENTHESES ||
                (scalaSettings.SPACE_BEFORE_INFIX_LIKE_METHOD_PARENTHESES && ScalaNamesUtil.isOperatorName(leftString)) ||
                (scalaSettings.PRESERVE_SPACE_AFTER_METHOD_DECLARATION_NAME &&
                        rightNode.getTreePrev.getPsi.isInstanceOf[PsiWhiteSpace]))
          return WITH_SPACING
        else return WITHOUT_SPACING
      case _: ScPrimaryConstructor =>
        return WITH_SPACING
      case _ =>
    }
    if (leftNode.getPsi.isInstanceOf[ScParameterClause] &&
            rightNode.getPsi.isInstanceOf[ScParameterClause]) {
      return WITHOUT_SPACING //todo: add setting
    }
    if (rightNode.getPsi.isInstanceOf[ScPatternArgumentList] &&
            rightNode.getTreeParent.getPsi.isInstanceOf[ScConstructorPattern]) {
      if (settings.SPACE_BEFORE_METHOD_CALL_PARENTHESES) return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (rightPsi.isInstanceOf[ScArguments] && rightNode.getTreeParent.getPsi.isInstanceOf[ScSelfInvocation]) {

    }

    //processing left parenthesis (if it's from left)
    if (leftNode.getElementType == ScalaTokenTypes.tLPARENTHESIS) {
      if (rightNode.getElementType == ScalaTokenTypes.tRPARENTHESIS)
        return WITHOUT_SPACING
      leftNode.getTreeParent.getPsi match {
        case _: ScForStatement =>
          if (settings.SPACE_WITHIN_FOR_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScIfStmt =>
          if (settings.SPACE_WITHIN_IF_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScWhileStmt | _: ScDoStmt =>
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
        case _: ScBindings =>
          if (settings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScFunctionalTypeElement =>
          if (settings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _ =>
      }
    }
    //processing right parenthesis (if it's from right)
    if (rightNode.getElementType == ScalaTokenTypes.tRPARENTHESIS) {
      if (leftNode.getElementType == ScalaTokenTypes.tLPARENTHESIS)
        return WITHOUT_SPACING
      rightNode.getTreeParent.getPsi match {
        case _: ScForStatement =>
          if (settings.SPACE_WITHIN_FOR_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScIfStmt =>
          if (settings.SPACE_WITHIN_IF_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScWhileStmt | _: ScDoStmt =>
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
        case _: ScBindings =>
          if (settings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScFunctionalTypeElement =>
          if (settings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        case _ =>
      }
    }

    //proccessing sqbrackets
    if (leftNode.getElementType == ScalaTokenTypes.tLSQBRACKET) {
      if (rightNode.getElementType == ScalaTokenTypes.tRSQBRACKET) {
        return WITHOUT_SPACING
      }
      else {
        if (settings.SPACE_WITHIN_BRACKETS) return WITH_SPACING
        else return WITHOUT_SPACING
      }
    }
    if (rightNode.getElementType == ScalaTokenTypes.tRSQBRACKET) {
      if (settings.SPACE_WITHIN_BRACKETS) return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (rightString.length > 0 &&
            rightString(0) == '[') {
      return WITHOUT_SPACING
    }

    //processing before left brace
    if (rightString.length > 0 && rightString(0) == '{' && rightNode.getElementType != ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START) {
      val parentPsi = rightNode.getTreeParent.getPsi

      parentPsi match {
        case _: ScTypeDefinition =>
          if (settings.SPACE_BEFORE_CLASS_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScFunction =>
          if (settings.SPACE_BEFORE_METHOD_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScIfStmt =>
          if (settings.SPACE_BEFORE_IF_LBRACE && !(leftNode.getElementType == ScalaTokenTypes.kELSE)) return WITH_SPACING
          else if (settings.SPACE_BEFORE_ELSE_LBRACE && leftNode.getElementType == ScalaTokenTypes.kELSE) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScWhileStmt =>
          if (settings.SPACE_BEFORE_WHILE_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScForStatement =>
          if (settings.SPACE_BEFORE_FOR_LBRACE && leftNode.getElementType != ScalaTokenTypes.kFOR) return WITH_SPACING
          else if (leftNode.getElementType == ScalaTokenTypes.kFOR) return WITHOUT_SPACING
          else return WITHOUT_SPACING
        case _: ScDoStmt =>
          if (settings.SPACE_BEFORE_DO_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScMatchStmt =>
          if (scalaSettings.SPACE_BEFORE_MATCH_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScTryBlock =>
          if (settings.SPACE_BEFORE_TRY_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScCatchBlock =>
          if (settings.SPACE_BEFORE_CATCH_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScFinallyBlock =>
          if (settings.SPACE_BEFORE_FINALLY_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        case _: ScExistentialClause =>
          return WITH_SPACING //todo: add setting
        case _: ScAnnotationExpr =>
          return WITH_SPACING //todo: add setting
        case _: ScExtendsBlock =>
          return WITH_SPACING //todo: add setting
        case _: ScPackaging =>
          return WITH_SPACING //todo: add setting
        case _ =>
          return WITH_SPACING
      }
    }





    //special for "case <caret> =>" (for SurroundWith)
    if (leftNode.getElementType == ScalaTokenTypes.kCASE &&
            rightNode.getElementType == ScalaTokenTypes.tFUNTYPE) return Spacing.createSpacing(2, 2, 0, false, 0)

    //Case Clauses case
    if (leftNode.getElementType == ScalaElementTypes.CASE_CLAUSE && rightNode.getElementType == ScalaElementTypes.CASE_CLAUSE) {
      val block = leftNode.getTreeParent
      val minLineFeeds = if (block.getTextRange.substring(fileText).contains("\n")) 1 else 0
      return WITH_SPACING_DEPENDENT(leftNode.getTreeParent.getTreeParent.getTextRange)
    }



    (leftNode.getElementType, rightNode.getElementType,
            leftNode.getTreeParent.getElementType, rightNode.getTreeParent.getElementType) match {
      case (ScalaTokenTypes.tFUNTYPE, ScalaElementTypes.PARAM_CLAUSES, ScalaElementTypes.FUNCTION_EXPR, _) //TODO: is this even ever used?
        if !scalaSettings.PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE =>
        if (rightString.startsWith("{")) WITH_SPACING
        else if (leftNode.getTreeParent.getTextRange.substring(fileText).contains("\n")) ON_NEW_LINE
        else WITH_SPACING
      //annotation
      case (_, ScalaElementTypes.ANNOTATIONS, ScalaElementTypes.ANNOT_TYPE, _) => WITHOUT_SPACING
      //case for package statement
      case (ScalaElementTypes.REFERENCE, ret, _, _) if ret != ScalaElementTypes.PACKAGING &&
              leftNode.getTreePrev != null && leftNode.getTreePrev.getTreePrev != null &&
              leftNode.getTreePrev.getTreePrev.getElementType == ScalaTokenTypes.kPACKAGE => DOUBLE_LINE
      case (ScalaElementTypes.REFERENCE, ScalaElementTypes.PACKAGING, _, _) if leftNode.getTreePrev != null &&
              leftNode.getTreePrev.getTreePrev != null &&
              leftNode.getTreePrev.getTreePrev.getElementType == ScalaTokenTypes.kPACKAGE => ON_NEW_LINE
      //case for covariant or contrvariant type params
      case (ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tIDENTIFIER, ScalaElementTypes.TYPE_PARAM, ScalaElementTypes.TYPE_PARAM) => NO_SPACING

      //class params
      case (ScalaTokenTypes.tIDENTIFIER | ScalaElementTypes.TYPE_PARAM_CLAUSE, ScalaElementTypes.PRIMARY_CONSTRUCTOR, _, _)
        if rightNode.getPsi.asInstanceOf[ScPrimaryConstructor].annotations.isEmpty &&
                !rightNode.getPsi.asInstanceOf[ScPrimaryConstructor].hasModifier => NO_SPACING
      //Type*
      case (_, ScalaTokenTypes.tIDENTIFIER, _, ScalaElementTypes.PARAM_TYPE) if rightString == "*" => NO_SPACING
      //Parameters
      case (ScalaTokenTypes.tIDENTIFIER, ScalaElementTypes.PARAM_CLAUSES, _, _) => NO_SPACING
      case (_, ScalaElementTypes.TYPE_ARGS, _, (ScalaElementTypes.TYPE_GENERIC_CALL | ScalaElementTypes.GENERIC_CALL)) => NO_SPACING
      case (_, ScalaElementTypes.PATTERN_ARGS, _, ScalaElementTypes.CONSTRUCTOR_PATTERN) => NO_SPACING
      //Annotation
      case (ScalaTokenTypes.tAT, _, _, _) if rightPsi.isInstanceOf[ScXmlPattern] => WITH_SPACING
      case (ScalaTokenTypes.tAT, _, _, _) => NO_SPACING
      case (ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tAT, ScalaElementTypes.NAMING_PATTERN, _) => NO_SPACING
      case (_, ScalaTokenTypes.tAT, _, _) => NO_SPACING_WITH_NEWLINE
      case (ScalaElementTypes.ANNOTATION, _, _, _) => COMMON_SPACING
      //Prefix Identifier
      case ((ScalaElementTypes.REFERENCE_EXPRESSION | ScalaTokenTypes.tIDENTIFIER), _,
      (ScalaElementTypes.LITERAL | ScalaElementTypes.PREFIX_EXPR
              | ScalaElementTypes.VARIANT_TYPE_PARAM), _) => NO_SPACING
      //Braces
      case (ScalaTokenTypes.tLBRACE, ScalaTokenTypes.tRBRACE, _, _) => NO_SPACING
      case (ScalaTokenTypes.tLBRACE, _,
      (ScalaElementTypes.TEMPLATE_BODY | ScalaElementTypes.MATCH_STMT | ScalaElementTypes.REFINEMENT |
              ScalaElementTypes.EXISTENTIAL_CLAUSE | ScalaElementTypes.BLOCK_EXPR), _) => IMPORT_BETWEEN_SPACING
      case (ScalaTokenTypes.tLBRACE, _, _, _) => NO_SPACING_WITH_NEWLINE
      case (_, ScalaTokenTypes.tRBRACE, (ScalaElementTypes.TEMPLATE_BODY | ScalaElementTypes.MATCH_STMT | ScalaElementTypes.REFINEMENT |
              ScalaElementTypes.EXISTENTIAL_CLAUSE | ScalaElementTypes.BLOCK_EXPR), _) => IMPORT_BETWEEN_SPACING
      case (_, ScalaTokenTypes.tRBRACE, _, _) => NO_SPACING_WITH_NEWLINE
      //Semicolon
      case (ScalaTokenTypes.tSEMICOLON, _, parentType, _) =>
        if ((BLOCK_ELEMENT_TYPES contains parentType) && !getText(leftNode.getTreeParent, fileText).contains("\n")) COMMON_SPACING
        else IMPORT_BETWEEN_SPACING
      case (_, ScalaTokenTypes.tSEMICOLON, _, _) =>
        NO_SPACING
      //Imports
      case (ScalaElementTypes.IMPORT_STMT, ScalaElementTypes.IMPORT_STMT, _, _) => IMPORT_BETWEEN_SPACING
      case (ScalaElementTypes.IMPORT_STMT, _, ScalaElementTypes.FILE, _) => DOUBLE_LINE
      case (ScalaElementTypes.IMPORT_STMT, _, ScalaElementTypes.PACKAGING, _) => DOUBLE_LINE
      case (ScalaElementTypes.IMPORT_STMT, _, _, _) => IMPORT_BETWEEN_SPACING
      //Dot
      case (ScalaTokenTypes.tDOT, _, _, _) => NO_SPACING_WITH_NEWLINE
      case (_, ScalaTokenTypes.tDOT, _, _) => NO_SPACING
      //Comma
      case (ScalaTokenTypes.tCOMMA, _, _, _) => COMMON_SPACING
      case (_, ScalaTokenTypes.tCOMMA, _, _) => NO_SPACING
      //Parenthesises and Brackets
      case ((ScalaTokenTypes.tLPARENTHESIS | ScalaTokenTypes.tLSQBRACKET), _, _, _) => NO_SPACING_WITH_NEWLINE
      case (_, ScalaTokenTypes.tLSQBRACKET, _, _) => NO_SPACING
      case (_, ScalaTokenTypes.tLPARENTHESIS, ScalaElementTypes.CONSTRUCTOR_PATTERN, _) => NO_SPACING
      case ((ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tRSQBRACKET), _, _, _) => COMMON_SPACING
      case (_, (ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tRSQBRACKET), _, _) => NO_SPACING_WITH_NEWLINE
      //Case clauses
      case (ScalaElementTypes.CASE_CLAUSE, _, _, _) => IMPORT_BETWEEN_SPACING
      case (_, ScalaElementTypes.CASE_CLAUSE, _, _) => IMPORT_BETWEEN_SPACING
      //#
      case (ScalaTokenTypes.tINNER_CLASS, _, _, _) => NO_SPACING
      case (ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER, _, _) =>
        leftNode.getPsi.getNextSibling match {
          case _: PsiWhiteSpace => COMMON_SPACING
          case _ => NO_SPACING
        }
      case (_, ScalaTokenTypes.tINNER_CLASS, _, _) => NO_SPACING

      //Other cases
      case _ =>
        COMMON_SPACING
    }
  }
}
