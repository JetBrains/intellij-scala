package org.jetbrains.plugins.scala
package lang
package formatting
package processors

import com.intellij.psi.tree.TokenSet
import psi.api.ScalaFile
import scaladoc.psi.api.ScDocComment
import scaladoc.lexer.ScalaDocTokenType
import settings.ScalaCodeStyleSettings
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx
import com.intellij.psi.xml._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._

import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import com.intellij.formatting.Spacing
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.{PsiElement, PsiComment, PsiWhiteSpace}
import psi.api.toplevel.imports. {ScImportSelectors, ScImportStmt}

object ScalaSpacingProcessor extends ScalaTokenTypes {
  val NO_SPACING_WITH_NEWLINE = Spacing.createSpacing(0, 0, 0, true, 1);
  val NO_SPACING = Spacing.createSpacing(0, 0, 0, false, 0);
  val COMMON_SPACING = Spacing.createSpacing(1, 1, 0, true, 100);
  val IMPORT_BETWEEN_SPACING = Spacing.createSpacing(0, 0, 1, true, 100);
  val IMPORT_OTHER_SPACING = Spacing.createSpacing(0, 0, 2, true, 100);

  val BLOCK_ELEMENT_TYPES = {
    import ScalaElementTypes._
    TokenSet.create(BLOCK_EXPR, TEMPLATE_BODY, PACKAGING, TRY_BLOCK, MATCH_STMT, CATCH_BLOCK)
  }

  private def getText(node: ASTNode, fileText: String): String = {
    node.getTextRange.substring(fileText)
  }

  private def nextNotWithspace(elem: PsiElement): PsiElement = {
    var next = elem.getNextSibling
    while (next != null && (next.isInstanceOf[PsiWhiteSpace] ||
            next.getNode.getElementType == ScalaTokenTypes.tLINE_TERMINATOR)) next = next.getNextSibling
    next
  }

  private def prevNotWithspace(elem: PsiElement): PsiElement = {
    var prev = elem.getPrevSibling
    while (prev != null && (prev.isInstanceOf[PsiWhiteSpace] ||
            prev.getNode.getElementType == ScalaTokenTypes.tLINE_TERMINATOR)) prev = prev.getPrevSibling
    prev
  }

  def getSpacing(left: ScalaBlock, right: ScalaBlock): Spacing = {
    val settings = left.getSettings
    val scalaSettings: ScalaCodeStyleSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val keepLineBreaks = settings.KEEP_LINE_BREAKS
    def getSpacing(x: Int, y: Int, z: Int) = {
      if (keepLineBreaks) Spacing.createSpacing(y, y, z, true, x)
      else Spacing.createSpacing(y, y, z, false, 0)
    }
    def getDependentLFSpacing(x: Int, y: Int, range: TextRange) = {
      if (keepLineBreaks) Spacing.createDependentLFSpacing(y, y, range, true, x)
      else Spacing.createDependentLFSpacing(y, y, range, false, 0)
    }
    val keepBlankLinesInCode = settings.KEEP_BLANK_LINES_IN_CODE
    val keepBlankLinesInDeclarations = settings.KEEP_BLANK_LINES_IN_DECLARATIONS
    val keepBlankLinesBeforeRBrace = settings.KEEP_BLANK_LINES_BEFORE_RBRACE
    val WITHOUT_SPACING = getSpacing(keepBlankLinesInCode, 0, 0)
    val WITHOUT_SPACING_DEPENDENT = (range: TextRange) => getDependentLFSpacing(keepBlankLinesInCode, 0, range)
    val WITH_SPACING = getSpacing(keepBlankLinesInCode, 1, 0)
    val WITH_SPACING_DEPENDENT = (range: TextRange) => getDependentLFSpacing(keepBlankLinesInCode, 1, range)
    val ON_NEW_LINE = getSpacing(keepBlankLinesInCode, 0, 1)
    val DOUBLE_LINE = getSpacing(keepBlankLinesInCode, 0, 2)
    def CONCRETE_LINES(x: Int) = Spacing.createSpacing(0, 0, x, keepLineBreaks, keepBlankLinesInCode)
    val leftNode = left.getNode
    val rightNode = right.getNode
    val fileText = leftNode.getPsi.getContainingFile.getText
    def nodeText(node: ASTNode): String = {
      getText(node, fileText)
    }

    //new formatter spacing
    val leftElementType = leftNode.getElementType
    val rightElementType = rightNode.getElementType
    val leftPsi = leftNode.getPsi
    val rightPsi = rightNode.getPsi
    import ScalaTokenTypes._
    import ScalaElementTypes._
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
            rightPsi.isInstanceOf[ScArgumentExprList]) {
      if (settings.SPACE_BEFORE_METHOD_CALL_PARENTHESES) return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (leftElementType == tLPARENTHESIS && (leftPsi.getParent.isInstanceOf[ScArgumentExprList] ||
            leftPsi.getParent.isInstanceOf[ScPatternArgumentList])) {
      if (settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE) {
        if (settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES) return WITH_SPACING_DEPENDENT(leftPsi.getParent.getTextRange)
        else return WITHOUT_SPACING_DEPENDENT(leftPsi.getParent.getTextRange)
      } else if (settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES) return WITHOUT_SPACING
      else return WITHOUT_SPACING
    }
    if (rightElementType == tRPARENTHESIS && (rightPsi.getParent.isInstanceOf[ScArgumentExprList] ||
            rightPsi.getParent.isInstanceOf[ScPatternArgumentList])) {
      if (settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE) {
        if (settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES) return WITH_SPACING_DEPENDENT(rightPsi.getParent.getTextRange)
        else return WITHOUT_SPACING_DEPENDENT(rightPsi.getParent.getTextRange)
      } else if (settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES) return WITHOUT_SPACING
      else return WITHOUT_SPACING
    }
    //todo: spacing for {} arguments
    //todo: spacing for early definitions
    if (getText(rightNode, fileText).startsWith("{")) {
      if (rightPsi.isInstanceOf[ScImportSelectors]) {
        return WITHOUT_SPACING
      }
      if (rightPsi.isInstanceOf[ScExtendsBlock] || rightPsi.isInstanceOf[ScTemplateBody]) {
        val extendsBlock = rightPsi match {
          case e: ScExtendsBlock => e
          case t: ScTemplateBody => t.getParent
        }
        settings.CLASS_BRACE_STYLE match {
          case CommonCodeStyleSettings.NEXT_LINE => return ON_NEW_LINE
          case CommonCodeStyleSettings.NEXT_LINE_SHIFTED => return ON_NEW_LINE
          case CommonCodeStyleSettings.NEXT_LINE_SHIFTED2 => return ON_NEW_LINE
          case CommonCodeStyleSettings.END_OF_LINE => {
            if (settings.SPACE_BEFORE_CLASS_LBRACE) return WITH_SPACING
            else return WITHOUT_SPACING
          }
          case CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED => {
            val startOffset = extendsBlock.getParent.getParent match {
              case b: ScTypeDefinition => b.nameId.getTextRange.getStartOffset
              case b: ScTemplateDefinition => b.nameId.getTextRange.getStartOffset
              case b => b.getTextRange.getStartOffset
            }
            val range = new TextRange(startOffset, rightPsi.getTextRange.getStartOffset)
            if (settings.SPACE_BEFORE_CLASS_LBRACE) return WITH_SPACING_DEPENDENT(range)
            else return WITHOUT_SPACING_DEPENDENT(range)
          }
        }
      } else {
        rightPsi.getParent match {
          case fun: ScFunction => {
            settings.CLASS_BRACE_STYLE match {
              case CommonCodeStyleSettings.NEXT_LINE => return ON_NEW_LINE
              case CommonCodeStyleSettings.NEXT_LINE_SHIFTED => return ON_NEW_LINE
              case CommonCodeStyleSettings.NEXT_LINE_SHIFTED2 => return ON_NEW_LINE
              case CommonCodeStyleSettings.END_OF_LINE => {
                if (settings.SPACE_BEFORE_METHOD_LBRACE) return WITH_SPACING
                else return WITHOUT_SPACING
              }
              case CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED => {
                val startOffset = fun.nameId.getTextRange.getStartOffset
                val range = new TextRange(startOffset, rightPsi.getTextRange.getStartOffset)
                if (settings.SPACE_BEFORE_METHOD_LBRACE) return WITH_SPACING_DEPENDENT(range)
                else return WITHOUT_SPACING_DEPENDENT(range)
              }
            }
          }
          case parent => {
            settings.CLASS_BRACE_STYLE match {
              case CommonCodeStyleSettings.NEXT_LINE => return ON_NEW_LINE
              case CommonCodeStyleSettings.NEXT_LINE_SHIFTED => return ON_NEW_LINE
              case CommonCodeStyleSettings.NEXT_LINE_SHIFTED2 => return ON_NEW_LINE
              case CommonCodeStyleSettings.END_OF_LINE => {
                return WITH_SPACING //todo: spacing settings
              }
              case CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED => {
                val startOffset = parent.getTextRange.getStartOffset
                val range = new TextRange(startOffset, rightPsi.getTextRange.getStartOffset)
                return WITH_SPACING_DEPENDENT(range) //todo: spacing settings
              }
            }
          }
        }
      }
    }


    if (leftPsi.isInstanceOf[ScStableCodeReferenceElement]) {
      leftPsi.getParent match {
        case p: ScPackaging if p.reference == Some(leftPsi) => {
          if (rightElementType != ScalaTokenTypes.tSEMICOLON && rightElementType != ScalaTokenTypes.tLBRACE) {
            return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AFTER_PACKAGE + 1, keepLineBreaks, keepBlankLinesInCode)
          }
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
      return Spacing.createSpacing(0, 0, settings.BLANK_LINES_BEFORE_PACKAGE + 1, keepLineBreaks, keepBlankLinesInCode)
    }

    if (leftPsi.isInstanceOf[ScImportStmt] && !rightPsi.isInstanceOf[ScImportStmt]) {
      if (rightElementType != ScalaTokenTypes.tSEMICOLON) {
        leftPsi.getParent match {
          case _: ScTemplateBody | _: ScalaFile | _: ScPackaging => {
            return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AFTER_IMPORTS + 1, keepLineBreaks,
              keepBlankLinesInCode)
          }
          case _ =>
        }
      } else if (settings.SPACE_BEFORE_SEMICOLON) return WITH_SPACING
      else return WITHOUT_SPACING
    }

    if (rightPsi.isInstanceOf[ScImportStmt] && !leftPsi.isInstanceOf[ScImportStmt]) {
      if (leftElementType != ScalaTokenTypes.tSEMICOLON || !prevNotWithspace(leftPsi).isInstanceOf[ScImportStmt]) {
        rightPsi.getParent match {
          case _: ScTemplateBody | _: ScalaFile | _: ScPackaging => {
            return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AFTER_IMPORTS + 1, keepLineBreaks,
              keepBlankLinesInCode)
          }
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
          case _: ScTemplateBody | _: ScalaFile | _: ScPackaging => {
            return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AROUND_CLASS + 1, keepLineBreaks,
              keepBlankLinesInDeclarations)
          }
          case _ =>
        }
      }
    }

    if (rightPsi.isInstanceOf[ScTypeDefinition]) {
      rightPsi.getParent match {
        case _: ScTemplateBody | _: ScalaFile | _: ScPackaging => {
          return Spacing.createSpacing(0, 0, settings.BLANK_LINES_AROUND_CLASS + 1, keepLineBreaks,
            keepBlankLinesInDeclarations)
        }
        case _ =>
      }
    }

    if (rightNode.getElementType == ScalaTokenTypes.tRBRACE) {
      rightNode.getTreeParent.getPsi match {
        case block@(_: ScTemplateBody | _: ScPackaging | _: ScBlockExpr | _: ScMatchStmt |
                _: ScTryBlock | _: ScCatchBlock) => {
          return Spacing.createDependentLFSpacing(0, 0, block.getTextRange, keepLineBreaks, keepBlankLinesBeforeRBrace)
        }
        case _ => return Spacing.createSpacing(0, 0, 0, keepLineBreaks, keepBlankLinesBeforeRBrace)
      }
    }

    if (leftNode.getElementType == ScalaTokenTypes.tLBRACE) {
      rightNode.getElementType match {
        case ScalaElementTypes.FUNCTION_EXPR => {
          if (!scalaSettings.PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE) return WITHOUT_SPACING
        }
        case _ =>
      }
      leftNode.getTreeParent.getPsi match {
        case b: ScTemplateBody => {
          val c = PsiTreeUtil.getParentOfType(b, classOf[ScTemplateDefinition])
          val setting = if (c.isInstanceOf[ScTypeDefinition]) settings.BLANK_LINES_AFTER_CLASS_HEADER
          else settings.BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER
          return Spacing.createSpacing(0, 0, setting + 1, keepLineBreaks, keepBlankLinesInDeclarations)
        }
        case b: ScBlockExpr if b.getParent.isInstanceOf[ScFunction] => {
          return Spacing.createSpacing(0, 0, settings.BLANK_LINES_BEFORE_METHOD_BODY + 1, keepLineBreaks, keepBlankLinesInDeclarations)
        }
        case block@(_: ScPackaging | _: ScBlockExpr | _: ScMatchStmt |
                _: ScTryBlock | _: ScCatchBlock) => {
          return Spacing.createDependentLFSpacing(0, 0, block.getTextRange, keepLineBreaks, keepBlankLinesBeforeRBrace)
        }
        case _ => return Spacing.createSpacing(0, 0, 0, keepLineBreaks, keepBlankLinesBeforeRBrace)
      }
    }

    if (leftPsi.isInstanceOf[ScFunction] || leftPsi.isInstanceOf[ScValue] || leftPsi.isInstanceOf[ScVariable] || leftPsi.isInstanceOf[ScTypeAlias]) {
      if (rightElementType != tSEMICOLON) {
        leftPsi.getParent match {
          case b: ScTemplateBody => {
            val p = PsiTreeUtil.getParentOfType(b, classOf[ScTemplateDefinition])
            val setting = if (leftPsi.isInstanceOf[ScFunction] && p.isInstanceOf[ScTrait]) settings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE
            else if (leftPsi.isInstanceOf[ScFunction]) settings.BLANK_LINES_AROUND_METHOD
            else if (rightPsi.isInstanceOf[ScFunction] && p.isInstanceOf[ScTrait]) settings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE
            else if (rightPsi.isInstanceOf[ScFunction]) settings.BLANK_LINES_AROUND_METHOD
            else if (p.isInstanceOf[ScTrait]) settings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE
            else settings.BLANK_LINES_AROUND_FIELD
            return Spacing.createSpacing(0, 0, setting + 1, keepLineBreaks, keepBlankLinesInDeclarations)
          }
          case _ =>
        }
      }
    }

    if (rightPsi.isInstanceOf[ScFunction] || rightPsi.isInstanceOf[ScValue] || rightPsi.isInstanceOf[ScVariable] || rightPsi.isInstanceOf[ScTypeAlias]) {
      rightPsi.getParent match {
        case b: ScTemplateBody => {
          val p = PsiTreeUtil.getParentOfType(b, classOf[ScTemplateDefinition])
          val setting = if (rightPsi.isInstanceOf[ScFunction] && p.isInstanceOf[ScTrait]) settings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE
          else if (rightPsi.isInstanceOf[ScFunction]) settings.BLANK_LINES_AROUND_METHOD
          else if (p.isInstanceOf[ScTrait]) settings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE
          else settings.BLANK_LINES_AROUND_FIELD
          return Spacing.createSpacing(0, 0, setting + 1, keepLineBreaks, keepBlankLinesInDeclarations)
        }
        case _ =>
      }
    }

    //old formatter spacing

    /**
     * This is not nodes text! This is blocks text, which can be different from node.
     */
    val (leftString, rightString) = (left.getTextRange.substring(fileText),
            right.getTextRange.substring(fileText))
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
      if (scalaSettings.SPACE_BEFORE_COLON) return WITH_SPACING  //todo:
      while (left != null && left.getLastChildNode != null) {
        left = left.getLastChildNode
      }
      val tp = PsiTreeUtil.getParentOfType(left.getPsi, classOf[ScTypeParam])
      if (tp ne null) {          
          return if (tp.nameId.getNode eq left) WITHOUT_SPACING else WITH_SPACING
      }
      return if (left.getElementType == ScalaTokenTypes.tIDENTIFIER &&
              !getText(left, fileText).matches(".*[A-Za-z0-9]")) WITH_SPACING else WITHOUT_SPACING
    }
    if (rightString.length > 0 && rightString(0) == ';') {
      if (settings.SPACE_BEFORE_SEMICOLON && !(rightNode.getTreeParent.getPsi.isInstanceOf[ScalaFile]) &&
              rightNode.getPsi.getParent.getParent.isInstanceOf[ScForStatement]) return WITH_SPACING
      else if (!(rightNode.getTreeParent.getPsi.isInstanceOf[ScalaFile]) &&
              rightNode.getPsi.getParent.getParent.isInstanceOf[ScForStatement]) return WITHOUT_SPACING
    }
    if (leftString.length > 0 && leftString(leftString.length - 1) == '.') {
      return WITHOUT_SPACING
    }
    if (leftString.length > 0 && leftString(leftString.length - 1) == ',') {
      if (scalaSettings.SPACE_AFTER_COMMA) return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (leftNode.getElementType == ScalaTokenTypes.tCOLON) {
      if (scalaSettings.SPACE_AFTER_COLON) return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (leftString.length > 0 && leftString(leftString.length - 1) == ';') {
      if (scalaSettings.SPACE_AFTER_SEMICOLON && !(rightNode.getTreeParent.getPsi.isInstanceOf[ScalaFile]) &&
              rightNode.getPsi.getParent.getParent.isInstanceOf[ScForStatement]) return WITH_SPACING
      else if (!(rightNode.getTreeParent.getPsi.isInstanceOf[ScalaFile]) &&
              rightNode.getPsi.getParent.getParent.isInstanceOf[ScForStatement]) return WITHOUT_SPACING
    }

    if (leftNode.getElementType == ScalaTokenTypes.tSEMICOLON) {
      if (getText(leftNode.getTreeParent, fileText).indexOf('\n') == -1) return WITH_SPACING
      else ON_NEW_LINE
    }

    //processing left parenthesis (if it's from right) as Java cases
    if (rightNode.getElementType == ScalaTokenTypes.tLPARENTHESIS) {
      leftNode.getElementType match {
        case ScalaTokenTypes.kIF => {
          if (scalaSettings.SPACE_BEFORE_IF_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case ScalaTokenTypes.kWHILE => {
          if (scalaSettings.SPACE_BEFORE_WHILE_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case ScalaTokenTypes.kFOR => {
          if (scalaSettings.SPACE_BEFORE_FOR_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _ =>
      }
    }
    if (rightNode.getPsi.isInstanceOf[ScParameters] &&
            leftNode.getTreeParent.getPsi.isInstanceOf[ScFunction]) {
      if (scalaSettings.SPACE_BEFORE_METHOD_PARENTHESES) return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (rightNode.getPsi.isInstanceOf[ScArguments] &&
            (leftNode.getTreeParent.getPsi.isInstanceOf[ScMethodCall] ||
                    leftNode.getTreeParent.getPsi.isInstanceOf[ScConstructor])) {
      if (scalaSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES && !rightString.startsWith("{")) return WITH_SPACING
      else if (scalaSettings.SPACE_BEFORE_BRACE_METHOD_CALL && rightString.startsWith("{")) return WITH_SPACING
      else return WITHOUT_SPACING
    }

    //processing left parenthesis (if it's from right) only Scala cases
    if (rightNode.getPsi.isInstanceOf[ScParameters] &&
            leftNode.getTreeParent.getPsi.isInstanceOf[ScPrimaryConstructor]) {
      if (scalaSettings.SPACE_BEFORE_METHOD_PARENTHESES) return WITH_SPACING
      else return WITHOUT_SPACING
    }
    if (rightNode.getPsi.isInstanceOf[ScPrimaryConstructor] &&
            rightString.length > 0 &&
            rightString.substring(0, 1) == "(") {
      if (scalaSettings.SPACE_BEFORE_METHOD_PARENTHESES) return WITH_SPACING
      else return WITHOUT_SPACING
    } else if (rightNode.getPsi.isInstanceOf[ScPrimaryConstructor]) {
      return WITH_SPACING
    }
    if (leftNode.getPsi.isInstanceOf[ScParameterClause] &&
            rightNode.getPsi.isInstanceOf[ScParameterClause]) {
      return WITHOUT_SPACING //todo: add setting
    }
    if (rightNode.getPsi.isInstanceOf[ScPatternArgumentList] &&
            rightNode.getTreeParent.getPsi.isInstanceOf[ScConstructorPattern]) {
      if (scalaSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES) return WITH_SPACING
      else return WITHOUT_SPACING
    }

    //processing left parenthesis (if it's from left)
    if (leftNode.getElementType == ScalaTokenTypes.tLPARENTHESIS) {
      if (rightNode.getElementType == ScalaTokenTypes.tRPARENTHESIS)
        return WITHOUT_SPACING
      leftNode.getTreeParent.getPsi match {
        case _: ScForStatement => {
          if (scalaSettings.SPACE_WITHIN_FOR_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScIfStmt => {
          if (scalaSettings.SPACE_WITHIN_IF_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScWhileStmt | _: ScDoStmt => {
          if (scalaSettings.SPACE_WITHIN_WHILE_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScParenthesisedExpr => {
          if (scalaSettings.SPACE_WITHIN_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case x: ScParameterClause if x.getParent.getParent.isInstanceOf[ScFunction] => {
          if (scalaSettings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case x: ScParameterClause if x.getParent.getParent.isInstanceOf[ScPrimaryConstructor] => {
          if (scalaSettings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScPatternArgumentList => {
          if (scalaSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScArguments => {
          if (scalaSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScParenthesisedPattern => {
          if (scalaSettings.SPACE_WITHIN_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScTuplePattern => {
          WITHOUT_SPACING //todo: add setting
        }
        case _: ScParenthesisedTypeElement => {
          if (scalaSettings.SPACE_WITHIN_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScTupleTypeElement => {
          WITHOUT_SPACING //todo: add setting
        }
        case _: ScTuple => {
          WITHOUT_SPACING //todo: add setting
        }
        case _: ScBindings => {
          if (scalaSettings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScFunctionalTypeElement => {
          if (scalaSettings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _ =>
      }
    }
    //processing right parenthesis (if it's from right)
    if (rightNode.getElementType == ScalaTokenTypes.tRPARENTHESIS) {
      if (leftNode.getElementType == ScalaTokenTypes.tLPARENTHESIS)
        return WITHOUT_SPACING
      rightNode.getTreeParent.getPsi match {
        case _: ScForStatement => {
          if (scalaSettings.SPACE_WITHIN_FOR_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScIfStmt => {
          if (scalaSettings.SPACE_WITHIN_IF_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScWhileStmt | _: ScDoStmt => {
          if (scalaSettings.SPACE_WITHIN_WHILE_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScParenthesisedExpr => {
          if (scalaSettings.SPACE_WITHIN_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case x: ScParameterClause if x.getParent.getParent.isInstanceOf[ScFunction] => {
          if (scalaSettings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case x: ScParameterClause if x.getParent.getParent.isInstanceOf[ScPrimaryConstructor] => {
          if (scalaSettings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScPatternArgumentList => {
          if (scalaSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScArguments => {
          if (scalaSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScParenthesisedPattern => {
          if (scalaSettings.SPACE_WITHIN_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScTuplePattern => {
          WITHOUT_SPACING //todo: add setting
        }
        case _: ScParenthesisedTypeElement => {
          if (scalaSettings.SPACE_WITHIN_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScTupleTypeElement => {
          WITHOUT_SPACING //todo: add setting
        }
        case _: ScTuple => {
          WITHOUT_SPACING //todo: add setting
        }
        case _: ScBindings => {
          if (scalaSettings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScFunctionalTypeElement => {
          if (scalaSettings.SPACE_WITHIN_METHOD_PARENTHESES) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _ =>
      }
    }

    //proccessing sqbrackets
    if (leftNode.getElementType == ScalaTokenTypes.tLSQBRACKET) {
      if (rightNode.getElementType == ScalaTokenTypes.tRSQBRACKET) {
        return WITHOUT_SPACING
      }
      else {
        if (scalaSettings.SPACE_WITHIN_BRACKETS) return WITH_SPACING
        else return WITHOUT_SPACING
      }
    }
    if (rightNode.getElementType == ScalaTokenTypes.tRSQBRACKET) {
      if (scalaSettings.SPACE_WITHIN_BRACKETS) return WITH_SPACING
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
        case _: ScTypeDefinition => {
          if (scalaSettings.SPACE_BEFORE_CLASS_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScFunction => {
          if (scalaSettings.SPACE_BEFORE_METHOD_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScIfStmt => {
          if (scalaSettings.SPACE_BEFORE_IF_LBRACE && !(leftNode.getElementType == ScalaTokenTypes.kELSE)) return WITH_SPACING
          else if (scalaSettings.SPACE_BEFORE_ELSE_LBRACE && leftNode.getElementType == ScalaTokenTypes.kELSE) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScWhileStmt => {
          if (scalaSettings.SPACE_BEFORE_WHILE_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScForStatement => {
          if (scalaSettings.SPACE_BEFORE_FOR_LBRACE && leftNode.getElementType != ScalaTokenTypes.kFOR) return WITH_SPACING
          else if (leftNode.getElementType == ScalaTokenTypes.kFOR) return WITHOUT_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScDoStmt => {
          if (scalaSettings.SPACE_BEFORE_DO_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScMatchStmt => {
          if (scalaSettings.SPACE_BEFORE_MATCH_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScTryBlock => {
          if (scalaSettings.SPACE_BEFORE_TRY_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScCatchBlock => {
          if (scalaSettings.SPACE_BEFORE_CATCH_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScFinallyBlock => {
          if (scalaSettings.SPACE_BEFORE_FINALLY_LBRACE) return WITH_SPACING
          else return WITHOUT_SPACING
        }
        case _: ScExistentialClause => {
          return WITH_SPACING //todo: add setting
        }
        case _: ScAnnotationExpr => {
          return WITH_SPACING //todo: add setting
        }
        case _: ScExtendsBlock => {
          return WITH_SPACING //todo: add setting
        }
        case _: ScPackaging => {
          return WITH_SPACING //todo: add setting
        }
        case _ => {
          return WITH_SPACING
        }
      }
    }



    //special else if treatment
    if (leftNode.getElementType == ScalaTokenTypes.kELSE && rightNode.getPsi.isInstanceOf[ScIfStmt]) {
      if (scalaSettings.SPECIAL_ELSE_IF_TREATMENT) {
        return Spacing.createSpacing(1, 1, 0, false, 0)
      } else return ON_NEW_LINE
    }

    //special for "case <caret> =>" (for SurroundWith)
    if (leftNode.getElementType == ScalaTokenTypes.kCASE &&
            rightNode.getElementType == ScalaTokenTypes.tFUNTYPE) return Spacing.createSpacing(2, 2, 0, false, 0)

    //Case Clauses case
    if (leftNode.getElementType == ScalaElementTypes.CASE_CLAUSE && rightNode.getElementType == ScalaElementTypes.CASE_CLAUSE) {
      val block = leftNode.getTreeParent
      val minLineFeeds = if (block.getTextRange.substring(fileText).contains("\n")) 1 else 0
      return Spacing.createSpacing(1, 0, minLineFeeds, true, keepBlankLinesInCode)
    }



    (leftNode.getElementType, rightNode.getElementType,
            leftNode.getTreeParent.getElementType, rightNode.getTreeParent.getElementType) match {
      case (ScalaTokenTypes.tFUNTYPE, _, ScalaElementTypes.FUNCTION_EXPR, _)
        if !scalaSettings.PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE => {
        if (rightString.startsWith("{")) WITH_SPACING
        else if (leftNode.getTreeParent.getTextRange.substring(fileText).contains("\n")) ON_NEW_LINE
        else WITH_SPACING
      }
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
      case (ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tIDENTIFIER, ScalaElementTypes.TYPE_PARAM, ScalaElementTypes.TYPE_PARAM) => return NO_SPACING
      //xml
      case (ScalaElementTypes.XML_START_TAG, ScalaElementTypes.XML_END_TAG, _, _) => return WITHOUT_SPACING
      case (ScalaElementTypes.XML_START_TAG, XmlTokenType.XML_DATA_CHARACTERS, _, _) => return WITHOUT_SPACING
      case (XmlTokenType.XML_DATA_CHARACTERS, ScalaElementTypes.XML_END_TAG, _, _) => return WITHOUT_SPACING
      case (ScalaElementTypes.XML_START_TAG, _, _, _) => return ON_NEW_LINE
      case (_, ScalaElementTypes.XML_END_TAG, _, _) => return ON_NEW_LINE
      case (XmlTokenType.XML_DATA_CHARACTERS, XmlTokenType.XML_DATA_CHARACTERS, _, _) => return WITH_SPACING
      case (XmlTokenType.XML_DATA_CHARACTERS, _, _, _) => return ON_NEW_LINE
      case (_, XmlTokenType.XML_DATA_CHARACTERS, _, _) => return ON_NEW_LINE
      case (ScalaElementTypes.XML_EMPTY_TAG, ScalaElementTypes.XML_EMPTY_TAG, _, _) => return ON_NEW_LINE
      case (_, ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START | ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_END, _, _) => return NO_SPACING
      case (XmlTokenType.XML_START_TAG_START | XmlTokenType.XML_END_TAG_START |
              XmlTokenType.XML_CDATA_START | XmlTokenType.XML_PI_START, _, _, _) => return NO_SPACING
      case (_, XmlTokenType.XML_TAG_END | XmlTokenType.XML_EMPTY_ELEMENT_END |
              XmlTokenType.XML_CDATA_END | XmlTokenType.XML_PI_END, _, _) => return NO_SPACING
      case (XmlTokenType.XML_NAME, ScalaElementTypes.XML_ATTRIBUTE, _, _) => return COMMON_SPACING
      case (XmlTokenType.XML_NAME, XmlTokenType.XML_EQ, _, _) => return NO_SPACING
      case (XmlTokenType.XML_EQ, XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER |
              ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START, _, _) => return NO_SPACING
      case (XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER, XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, _, _) => return NO_SPACING
      case (XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER, _, _) => return NO_SPACING
      case (ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_START | ScalaTokenTypesEx.SCALA_IN_XML_INJECTION_END, _, _, _) => return NO_SPACING
      case (_, XmlTokenType.XML_DATA_CHARACTERS | XmlTokenType.XML_COMMENT_END
              | XmlTokenType.XML_COMMENT_CHARACTERS, _, _) => return NO_SPACING
      case (XmlTokenType.XML_DATA_CHARACTERS | XmlTokenType.XML_COMMENT_START
              | XmlTokenType.XML_COMMENT_CHARACTERS, _, _, _) => return NO_SPACING
      //class params
      case (ScalaTokenTypes.tIDENTIFIER | ScalaElementTypes.TYPE_PARAM_CLAUSE, ScalaElementTypes.PRIMARY_CONSTRUCTOR, _, _)
        if !rightNode.getPsi.asInstanceOf[ScPrimaryConstructor].hasAnnotation &&
                !rightNode.getPsi.asInstanceOf[ScPrimaryConstructor].hasModifier => return NO_SPACING
      //Type*
      case (_, ScalaTokenTypes.tIDENTIFIER, _, ScalaElementTypes.PARAM_TYPE) if (rightString == "*") => return NO_SPACING
      //Parameters
      case (ScalaTokenTypes.tIDENTIFIER, ScalaElementTypes.PARAM_CLAUSES, _, _) => return NO_SPACING
      case (_, ScalaElementTypes.TYPE_ARGS, _, (ScalaElementTypes.TYPE_GENERIC_CALL | ScalaElementTypes.GENERIC_CALL)) => return NO_SPACING
      case (_, ScalaElementTypes.PATTERN_ARGS, _, ScalaElementTypes.CONSTRUCTOR_PATTERN) => return NO_SPACING
      //Annotation
      case (ScalaTokenTypes.tAT, _, _, _) => return NO_SPACING
      case (ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tAT, ScalaElementTypes.NAMING_PATTERN, _) => return NO_SPACING
      case (_, ScalaTokenTypes.tAT, _, _) => return NO_SPACING_WITH_NEWLINE
      case (ScalaElementTypes.ANNOTATION, _, _, _) => return COMMON_SPACING
      //Prefix Identifier
      case ((ScalaElementTypes.REFERENCE_EXPRESSION | ScalaTokenTypes.tIDENTIFIER), _,
      (ScalaElementTypes.LITERAL | ScalaElementTypes.PREFIX_EXPR
              | ScalaElementTypes.VARIANT_TYPE_PARAM), _) => return NO_SPACING
      //Braces
      case (ScalaTokenTypes.tLBRACE, ScalaTokenTypes.tRBRACE, _, _) => NO_SPACING
      case (ScalaTokenTypes.tLBRACE, _,
      (ScalaElementTypes.TEMPLATE_BODY | ScalaElementTypes.MATCH_STMT | ScalaElementTypes.REFINEMENT |
              ScalaElementTypes.EXISTENTIAL_CLAUSE | ScalaElementTypes.BLOCK_EXPR), _) => return IMPORT_BETWEEN_SPACING
      case (ScalaTokenTypes.tLBRACE, _, _, _) => return NO_SPACING_WITH_NEWLINE
      case (_, ScalaTokenTypes.tRBRACE, (ScalaElementTypes.TEMPLATE_BODY | ScalaElementTypes.MATCH_STMT | ScalaElementTypes.REFINEMENT |
              ScalaElementTypes.EXISTENTIAL_CLAUSE | ScalaElementTypes.BLOCK_EXPR), _) => return IMPORT_BETWEEN_SPACING
      case (_, ScalaTokenTypes.tRBRACE, _, _) => return NO_SPACING_WITH_NEWLINE
      //Semicolon
      case (ScalaTokenTypes.tSEMICOLON, _, parentType, _) => {
        if ((BLOCK_ELEMENT_TYPES contains parentType) && !getText(leftNode.getTreeParent, fileText).contains("\n")) return COMMON_SPACING
        else return IMPORT_BETWEEN_SPACING
      }
      case (_, ScalaTokenTypes.tSEMICOLON, _, _) => {
        return NO_SPACING
      }
      //Imports
      case (ScalaElementTypes.IMPORT_STMT, ScalaElementTypes.IMPORT_STMT, _, _) => return IMPORT_BETWEEN_SPACING
      case (ScalaElementTypes.IMPORT_STMT, _, ScalaElementTypes.FILE, _) => return DOUBLE_LINE
      case (ScalaElementTypes.IMPORT_STMT, _, ScalaElementTypes.PACKAGING, _) => return DOUBLE_LINE
      case (ScalaElementTypes.IMPORT_STMT, _, _, _) => return IMPORT_BETWEEN_SPACING
      //Dot
      case (ScalaTokenTypes.tDOT, _, _, _) => return NO_SPACING_WITH_NEWLINE
      case (_, ScalaTokenTypes.tDOT, _, _) => return NO_SPACING
      //Comma
      case (ScalaTokenTypes.tCOMMA, _, _, _) => return COMMON_SPACING
      case (_, ScalaTokenTypes.tCOMMA, _, _) => return NO_SPACING
      //Parenthesises and Brackets
      case ((ScalaTokenTypes.tLPARENTHESIS | ScalaTokenTypes.tLSQBRACKET), _, _, _) => return NO_SPACING_WITH_NEWLINE
      case (_, ScalaTokenTypes.tLSQBRACKET, _, _) => return NO_SPACING
      case (_, ScalaTokenTypes.tLPARENTHESIS, ScalaElementTypes.CONSTRUCTOR_PATTERN, _) => return NO_SPACING
      case ((ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tRSQBRACKET), _, _, _) => return COMMON_SPACING
      case (_, (ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tRSQBRACKET), _, _) => return NO_SPACING_WITH_NEWLINE
      //Case clauses
      case (ScalaElementTypes.CASE_CLAUSE, _, _, _) => return IMPORT_BETWEEN_SPACING
      case (_, ScalaElementTypes.CASE_CLAUSE, _, _) => return IMPORT_BETWEEN_SPACING
      //#
      case (ScalaTokenTypes.tINNER_CLASS, _, _, _) => return NO_SPACING
      case (ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER, _, _) => {
        leftNode.getPsi.getNextSibling match {
          case _: PsiWhiteSpace => return COMMON_SPACING
          case _ => return NO_SPACING
        }
      }
      case (_, ScalaTokenTypes.tINNER_CLASS, _, _) => return NO_SPACING
      //ScalaDocs
      case (_, ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS, _, _) => return NO_SPACING_WITH_NEWLINE
      case (_, ScalaDocTokenType.DOC_COMMENT_END, _, _) => return NO_SPACING_WITH_NEWLINE
      case (ScalaDocTokenType.DOC_COMMENT_START, _, _, _) => return NO_SPACING_WITH_NEWLINE
      case (_, x, _, _) if ScalaDocTokenType.ALL_SCALADOC_TOKENS.contains(x)
              && getText(rightNode, fileText).apply(0) == ' ' => return NO_SPACING
      case (x, _, _, _) if ScalaDocTokenType.ALL_SCALADOC_TOKENS.contains(x)
              && getText(leftNode, fileText).apply(getText(leftNode, fileText).length - 1) == ' ' => return NO_SPACING
      //Other cases
      case _ => {
        return COMMON_SPACING
      }
    }
  }
}
