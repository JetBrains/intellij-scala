package org.jetbrains.plugins.scala
package lang
package formatting
/**
* @author ilyas
*/

import settings.ScalaCodeStyleSettings
import java.util.ArrayList
import psi.ScalaPsiUtil
import psi.api.statements._
import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiComment, PsiWhiteSpace, PsiElement}
import com.intellij.formatting._
import com.intellij.psi.tree._
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.formatting.processors._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import ScalaWrapManager._
import psi.api.toplevel.{ScEarlyDefinitions, ScModifierListOwner}
import scaladoc.lexer.ScalaDocTokenType
import scaladoc.parser.ScalaDocElementTypes
import scaladoc.psi.api.{ScDocTag, ScDocComment}
import scaladoc.parser.parsing.MyScaladocParsing
import com.intellij.formatting.Alignment.Anchor
import extensions.&&

object getDummyBlocks {
  val fieldGroupAlignmentKey: Key[Alignment] = Key.create("field.group.alignment.key")

  def apply(firstNode: ASTNode, lastNode: ASTNode, block: ScalaBlock): ArrayList[Block] =
    if (lastNode != null) applyInner(firstNode, lastNode, block) else applyInner(firstNode, block)


  private def applyInner(node: ASTNode, block: ScalaBlock): ArrayList[Block] = {
    val children = node.getChildren(null)
    val subBlocks = new ArrayList[Block]
    var prevChild: ASTNode = null
    val settings = block.getCommonSettings
    val scalaSettings = block.getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    node.getPsi match {
      case _: ScValue | _: ScVariable if settings.ALIGN_GROUP_FIELD_DECLARATIONS => {
        if (node.getTreeParent.getPsi match { case _: ScEarlyDefinitions | _: ScTemplateBody => true; case _ => false }) {
          subBlocks.addAll(getFieldGroupSubBlocks(node, block))
          return subBlocks
        }
      }
      case _: ScCaseClause if scalaSettings.ALIGN_IN_COLUMNS_CASE_BRANCH => {
        subBlocks.addAll(getCaseClauseGroupSubBlocks(node, block))
        return subBlocks
      }
      case _: ScIfStmt => {
        val alignment = if (scalaSettings.ALIGN_IF_ELSE) Alignment.createAlignment
                        else null
        subBlocks.addAll(getIfSubBlocks(node, block, alignment))
        return subBlocks
      }
      case _: ScInfixExpr | _: ScInfixPattern | _: ScInfixTypeElement => {
        subBlocks.addAll(getInfixBlocks(node, block))
        return subBlocks
      }
      case _: ScExtendsBlock => {
        subBlocks.addAll(getExtendsSubBlocks(node, block))
        return subBlocks
      }
      case _: ScReferenceExpression => {
        subBlocks.addAll(getMethodCallOrRefExprSubBlocks(node, block))
        return subBlocks
      }
      case _: ScMethodCall => {
        subBlocks.addAll(getMethodCallOrRefExprSubBlocks(node, block))
        return subBlocks
      }
      case _
        if node.getElementType == ScalaDocElementTypes.DOC_TAG =>
        val docTag = node.getPsi.asInstanceOf[ScDocTag]
        val tagConcernedNode = if (docTag.getValueElement != null) docTag.getValueElement.getNode else
          (if (docTag.getNameElement != null) docTag.getNameElement.getNode else null)

        if (tagConcernedNode != null) {
          var hasValidData = false
          var nextSiblTagVal = tagConcernedNode.getTreeNext
          while (!hasValidData && nextSiblTagVal != null) {
            if (nextSiblTagVal.getText.trim().length > 0 && nextSiblTagVal.getText != "*") hasValidData = true
            nextSiblTagVal = nextSiblTagVal.getTreeNext
          }

          if (hasValidData) {
            var nextSibl = docTag.getFirstChild.getNode
            while (nextSibl != tagConcernedNode.getTreeNext && subBlocks.size() < 3) {
              subBlocks.add(new ScalaBlock(block, nextSibl, null, null, Indent.getNoneIndent,
                arrangeSuggestedWrapForChild(block, nextSibl, scalaSettings, block.suggestedWrap), block.getSettings))

              nextSibl = nextSibl.getTreeNext
            }

            if (nextSibl != null) {
              val intBlock = new ScalaBlock(block, nextSibl, docTag.getLastChild.getNode, null, Indent.getNoneIndent,
                arrangeSuggestedWrapForChild(block, nextSibl, scalaSettings, block.suggestedWrap), block.getSettings)
              subBlocks.add(intBlock)
            }
            return subBlocks
          }
      }
      case _ =>
    }
    val alignment: Alignment = if (mustAlignment(node, block.getSettings))
      Alignment.createAlignment
    else null
    var alternateAlignment: Alignment = null
    for (val child <- children if isCorrectBlock(child)) {
      val indent = ScalaIndentProcessor.getChildIndent(block, child)
      val childAlignment: Alignment = {
        node.getPsi match {
          case _: ScParameterClause => {
            child.getElementType match {
              case ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tLPARENTHESIS => null
              case _ => alignment
            }
          }
          case args: ScArgumentExprList => {
            child.getElementType match {
              case ScalaTokenTypes.tRPARENTHESIS if args.missedLastExpr &&
                      settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS => alignment
              case ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tLPARENTHESIS => {
                if (settings.ALIGN_MULTILINE_METHOD_BRACKETS) {
                  if (alternateAlignment == null) {
                    alternateAlignment = Alignment.createAlignment
                  }
                  alternateAlignment
                } else null
              }
              case _ if settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS => alignment
              case _ => null
            }
          }
          case patt: ScPatternArgumentList => {
            child.getElementType match {
              case ScalaTokenTypes.tRPARENTHESIS if patt.missedLastExpr &&
                      settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS => alignment
              case ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tLPARENTHESIS => {
                if (settings.ALIGN_MULTILINE_METHOD_BRACKETS) {
                  if (alternateAlignment == null) {
                    alternateAlignment = Alignment.createAlignment
                  }
                  alternateAlignment
                } else null
              }
              case _ if settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS => alignment
              case _ => null
            }
          }
          case _: ScMethodCall | _: ScReferenceExpression => {
            if (child.getElementType == ScalaTokenTypes.tIDENTIFIER &&
                    child.getPsi.getParent.isInstanceOf[ScReferenceExpression] &&
                    child.getPsi.getParent.asInstanceOf[ScReferenceExpression].qualifier == None) null
            else if (child.getPsi.isInstanceOf[ScExpression]) null
            else alignment
          }
          case _: ScXmlStartTag  | _: ScXmlEmptyTag => {
            child.getElementType match {
              case ScalaElementTypes.XML_ATTRIBUTE => alignment
              case _ => null
            }
          }
          case _: ScXmlElement => {
            child.getElementType match {
              case ScalaElementTypes.XML_START_TAG | ScalaElementTypes.XML_END_TAG => alignment
              case _ => null
            }
          }
          case _ => alignment
        }
      }
      val childWrap = arrangeSuggestedWrapForChild(block, child, scalaSettings, block.suggestedWrap)
      subBlocks.add(new ScalaBlock(block, child, null, childAlignment, indent, childWrap, block.getSettings))
      prevChild = child
    }
    subBlocks
  }

  private def applyInner(node: ASTNode, lastNode: ASTNode, block: ScalaBlock): ArrayList[Block] = {
    val settings = block.getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val subBlocks = new ArrayList[Block]

    var child = node
    val normalAligment = Alignment.createAlignment(true)
    do {
      val indent = ScalaIndentProcessor.getChildIndent(block, child)
      if (isCorrectBlock(child) && !child.getPsi.isInstanceOf[ScTemplateParents]) {
        val (childAlignment, childWrap) = if ( node.getTreeParent.getElementType == ScalaDocElementTypes.DOC_TAG &&
                child.getElementType != ScalaDocTokenType.DOC_WHITESPACE &&
                child.getElementType != ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS &&
                child.getText.trim().length() > 0)
            (normalAligment, Wrap.createWrap(WrapType.NONE, false)) else
            (null, arrangeSuggestedWrapForChild(block, child, settings, block.suggestedWrap))

        subBlocks.add(new ScalaBlock(block, child, null, childAlignment, indent, childWrap, block.getSettings))
      } else if (isCorrectBlock(child)) {
        subBlocks.addAll(getTemplateParentsBlocks(child, block))
      }
    } while (child != lastNode && {child = child.getTreeNext; true})
    subBlocks
  }

  private def getCaseClauseGroupSubBlocks(node: ASTNode, block: ScalaBlock): ArrayList[Block] = {
    val children = node.getChildren(null)
    val subBlocks = new ArrayList[Block]
    var prevChild: ASTNode = null
    val scalaSettings = block.getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    for (val child <- children if isCorrectBlock(child)) {
      def getPrevGroupNode(node: ASTNode): ASTNode = {
        val nodePsi = node.getPsi
        var prev = nodePsi.getPrevSibling
        var breaks = 0
        def isOk(psi: PsiElement): Boolean = {
          if (psi.isInstanceOf[PsiWhiteSpace] || ScalaPsiUtil.isLineTerminator(psi)) {
            psi.getText.foreach(c => if (c == '\n') breaks += 1)
            return false
          }
          psi match {
            case _: ScCaseClause => {
              true
            }
            case _: PsiComment => false
            case _ => {
              breaks += 2
              false
            }
          }
        }
        while (prev != null && breaks <= 1 && !isOk(prev)) {
          prev = prev.getPrevSibling
        }
        if (breaks != 1) return null
        if (prev == null) return null
        prev.getNode
      }
      def getChildAlignment(node: ASTNode, child: ASTNode): Alignment = {
        val prev = getPrevGroupNode(node)
        def createNewAlignment: Alignment = {
          val alignment = Alignment.createAlignment(true)
          child.getPsi.putUserData(fieldGroupAlignmentKey, alignment)
          alignment
        }
        def getAlignment(node: ASTNode): Alignment = {
          val alignment = node.getPsi.getUserData(fieldGroupAlignmentKey)
          val newAlignment = if (alignment == null) createNewAlignment
          else alignment
          child.getPsi.putUserData(fieldGroupAlignmentKey, newAlignment)
          newAlignment
        }
        if (child.getElementType == ScalaTokenTypes.tFUNTYPE ||
                child.getElementType == ScalaTokenTypes.tFUNTYPE_ASCII) {
          if (prev == null) return createNewAlignment
          val prevChild =
            prev.findChildByType(TokenSet.create(ScalaTokenTypes.tFUNTYPE, ScalaTokenTypes.tFUNTYPE_ASCII))
          if (prevChild == null) {
            return getChildAlignment(prev, child)
          } else return getAlignment(prevChild)
        }
        null
      }
      val indent = ScalaIndentProcessor.getChildIndent(block, child)
      val childWrap = arrangeSuggestedWrapForChild(block, child, scalaSettings, block.suggestedWrap)
      val childAlignment = getChildAlignment(node, child)
      subBlocks.add(new ScalaBlock(block, child, null, childAlignment, indent, childWrap, block.getSettings))
      prevChild = child
    }
    subBlocks
  }

  private def getFieldGroupSubBlocks(node: ASTNode, block: ScalaBlock): ArrayList[Block] = {
    val children = node.getChildren(null)
    val subBlocks = new ArrayList[Block]
    var prevChild: ASTNode = null
    val scalaSettings = block.getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    for (val child <- children if isCorrectBlock(child)) {
      def getPrevGroupNode(node: ASTNode): ASTNode = {
        val nodePsi = node.getPsi
        var prev = nodePsi.getPrevSibling
        var breaks = 0
        def isOk(psi: PsiElement): Boolean = {
          if (psi.isInstanceOf[PsiWhiteSpace] || ScalaPsiUtil.isLineTerminator(psi)) {
            psi.getText.foreach(c => if (c == '\n') breaks += 1)
            return false
          }
          if (psi.getNode.getElementType == ScalaTokenTypes.tSEMICOLON) {
            return false
          }
          psi match {
            case _: ScVariableDeclaration | _: ScValueDeclaration if nodePsi.isInstanceOf[ScPatternDefinition] ||
              nodePsi.isInstanceOf[ScVariableDefinition] => {
              breaks += 2
              false
            }
            case _: ScVariableDefinition | _: ScPatternDefinition if nodePsi.isInstanceOf[ScValueDeclaration] ||
              nodePsi.isInstanceOf[ScValueDeclaration] => {
              breaks += 2
              false
            }
            case _: ScVariable | _: ScValue => {
              val hasMod1 = psi.isInstanceOf[ScModifierListOwner] &&
                      psi.asInstanceOf[ScModifierListOwner].getModifierList.getText == ""
              val hasMod2 = node.getPsi.isInstanceOf[ScModifierListOwner] &&
                      node.getPsi.asInstanceOf[ScModifierListOwner].getModifierList.getText == ""
              if (hasMod1 != hasMod2) {
                breaks += 2
                false
              } else {
                true
              }
            }
            case _: PsiComment => false
            case _ => {
              breaks += 2
              false
            }
          }
        }
        while (prev != null && breaks <= 1 && !isOk(prev)) {
          prev = prev.getPrevSibling
        }
        if (breaks != 1) return null
        if (prev == null) return null
        prev.getNode
      }
      def getChildAlignment(node: ASTNode, child: ASTNode): Alignment = {
        val prev = getPrevGroupNode(node)
        def createNewAlignment: Alignment = {
          val alignment = Alignment.createAlignment(true)
          child.getPsi.putUserData(fieldGroupAlignmentKey, alignment)
          alignment
        }
        def getAlignment(node: ASTNode): Alignment = {
          val alignment = node.getPsi.getUserData(fieldGroupAlignmentKey)
          val newAlignment = if (alignment == null) createNewAlignment
          else alignment
          child.getPsi.putUserData(fieldGroupAlignmentKey, newAlignment)
          newAlignment
        }
        if (child.getElementType == ScalaTokenTypes.tCOLON) {
          if (prev == null) return createNewAlignment
          val prevChild = prev.findChildByType(ScalaTokenTypes.tCOLON)
          if (prevChild == null) {
            return getChildAlignment(prev, child)
          } else return getAlignment(prevChild)
        } else if (child.getElementType == ScalaTokenTypes.tASSIGN) {
          if (prev == null) return createNewAlignment
          val prevChild = prev.findChildByType(ScalaTokenTypes.tASSIGN)
          if (prevChild == null) {
            return getChildAlignment(prev, child)
          } else return getAlignment(prevChild)
        } else if (child.getElementType == ScalaTokenTypes.kVAL ||
          child.getElementType == ScalaTokenTypes.kVAR) {
          if (prev == null) return createNewAlignment
          val prevChild = prev.findChildByType(TokenSet.create(ScalaTokenTypes.kVAL, ScalaTokenTypes.kVAR))
          if (prevChild == null) {
            return getChildAlignment(prev, child)
          } else return getAlignment(prevChild)
        }
        null
      }
      val indent = ScalaIndentProcessor.getChildIndent(block, child)
      val childWrap = arrangeSuggestedWrapForChild(block, child, scalaSettings, block.suggestedWrap)
      val childAlignment = getChildAlignment(node, child)
      subBlocks.add(new ScalaBlock(block, child, null, childAlignment, indent, childWrap, block.getSettings))
      prevChild = child
    }
    subBlocks
  }

  private def getTemplateParentsBlocks(node: ASTNode, block: ScalaBlock): ArrayList[Block] = {
    val settings = block.getSettings
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val subBlocks = new ArrayList[Block]
    val children = node.getChildren(null)
    val alignment = if (mustAlignment(node, settings))
      Alignment.createAlignment(true)
    else null
    for (val child <- children) {
      if (isCorrectBlock(child)) {
        val indent = ScalaIndentProcessor.getChildIndent(block, child)
        val childWrap = arrangeSuggestedWrapForChild(block, child, scalaSettings, block.suggestedWrap)
        subBlocks.add(new ScalaBlock(block, child, null, alignment, indent, childWrap, settings))
      }
    }
    subBlocks
  }

  private def getExtendsSubBlocks(node: ASTNode, block: ScalaBlock): ArrayList[Block] = {
    val settings = block.getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val subBlocks = new ArrayList[Block]
    val extBlock: ScExtendsBlock = node.getPsi.asInstanceOf[ScExtendsBlock]
    if (extBlock.getFirstChild == null) return subBlocks
    val tempBody = extBlock.templateBody
    val first = extBlock.getFirstChild
    val last = tempBody match {
      case None => extBlock.getLastChild
      case Some(x) => x.getPrevSibling
    }
    if (last != null) {
      val indent = ScalaIndentProcessor.getChildIndent(block, first.getNode)
      val childWrap = arrangeSuggestedWrapForChild(block, first.getNode, settings, block.suggestedWrap)
      subBlocks.add(new ScalaBlock(block, first.getNode, last.getNode, null, indent, childWrap, block.getSettings))
    }

    tempBody match {
      case Some(x) => {
        val indent = ScalaIndentProcessor.getChildIndent(block, x.getNode)
        val childWrap = arrangeSuggestedWrapForChild(block, x.getNode, settings, block.suggestedWrap)
        subBlocks.add(new ScalaBlock(block, x.getNode, null, null, indent, childWrap, block.getSettings))
      }
      case _ =>
    }
    subBlocks
  }

  private def getIfSubBlocks(node: ASTNode, block: ScalaBlock, alignment: Alignment): ArrayList[Block] = {
    val settings = block.getCommonSettings
    val scalaSettings = block.getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val subBlocks = new ArrayList[Block]
    val firstChildNode = node.getFirstChildNode
    var child = firstChildNode
    while (child.getTreeNext != null && child.getTreeNext.getElementType != ScalaTokenTypes.kELSE) {
      child = child.getTreeNext
    }
    val indent = ScalaIndentProcessor.getChildIndent(block, firstChildNode)
    val childWrap = arrangeSuggestedWrapForChild(block, firstChildNode, scalaSettings, block.suggestedWrap)
    val firstBlock = new ScalaBlock(block, firstChildNode, child, alignment, indent, childWrap, block.getSettings)
    subBlocks.add(firstBlock)
    if (child.getTreeNext != null) {
      val firstChild = child.getTreeNext
      child = firstChild
      while (child.getTreeNext != null) {
        child.getTreeNext.getPsi match {
          case _: ScIfStmt if settings.SPECIAL_ELSE_IF_TREATMENT => {
            val childWrap = arrangeSuggestedWrapForChild(block, firstChild, scalaSettings, block.suggestedWrap)
            subBlocks.add(new ScalaBlock(block, firstChild, child, alignment, indent, childWrap, block.getSettings))
            subBlocks.addAll(getIfSubBlocks(child.getTreeNext, block, alignment))
          }
          case _ =>
        }
        child = child.getTreeNext
      }
      if (subBlocks.size ==  1) {
        val childWrap = arrangeSuggestedWrapForChild(block, firstChild, scalaSettings, block.suggestedWrap)
        subBlocks.add(new ScalaBlock(block, firstChild, child, alignment, indent, childWrap, block.getSettings))
      }
    }
    subBlocks
  }

  private def getInfixBlocks(node: ASTNode, block: ScalaBlock, parentAlignment: Alignment = null): ArrayList[Block] = {
    val settings = block.getSettings
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val subBlocks = new ArrayList[Block]
    val children = node.getChildren(null)
    val alignment = if (parentAlignment != null)
      parentAlignment
    else if (mustAlignment(node, settings))
      Alignment.createAlignment
    else null
    for (val child <- children) {
      def checkSamePriority: Boolean = {
        import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils.priority
        val childPriority = child.getPsi match {
          case inf: ScInfixExpr => priority(inf.operation.getText, true)
          case inf: ScInfixPattern => priority(inf.refernece.getText, false)
          case inf: ScInfixTypeElement => priority(inf.ref.getText, false)
          case _ => 0
        }
        val parentPriority = node.getPsi match {
          case inf: ScInfixExpr => priority(inf.operation.getText, true)
          case inf: ScInfixPattern => priority(inf.refernece.getText, false)
          case inf: ScInfixTypeElement => priority(inf.ref.getText, false)
          case _ => 0
        }
        parentPriority == childPriority
      }
      if (INFIX_ELEMENTS.contains(child.getElementType) && checkSamePriority) {
        subBlocks.addAll(getInfixBlocks(child, block, alignment))
      } else if (isCorrectBlock(child)) {
        val indent = ScalaIndentProcessor.getChildIndent(block, child)

        val childWrap = arrangeSuggestedWrapForChild(block, child, scalaSettings, block.suggestedWrap)
        subBlocks.add(new ScalaBlock(block, child, null, alignment, indent, childWrap, settings))
      }
    }
    subBlocks
  }

  def getMethodCallOrRefExprSubBlocks(node: ASTNode, block: ScalaBlock, parentAlignment: Alignment = null): ArrayList[Block] = {
    val settings = block.getSettings
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val subBlocks = new ArrayList[Block]
    val children = node.getChildren(null)
    val alignment = if (parentAlignment != null)
      parentAlignment
    else if (mustAlignment(node, settings))
      Alignment.createAlignment
    else null
    for (val child <- children) {
      child.getPsi match {
        case methodCall: ScMethodCall => {
          subBlocks.addAll(getMethodCallOrRefExprSubBlocks(child, block, alignment))
        }
        case refExpr: ScReferenceExpression => {
          subBlocks.addAll(getMethodCallOrRefExprSubBlocks(child, block, alignment))
        }
        case _ => {
          if (isCorrectBlock(child)) {
            val indent = ScalaIndentProcessor.getChildIndent(block, child)
            val childWrap = arrangeSuggestedWrapForChild(block, child, scalaSettings, block.suggestedWrap)
            subBlocks.add(new ScalaBlock(block, child, null, alignment, indent, childWrap, settings))
          }
        }
      }
    }
    subBlocks
  }

  private def isCorrectBlock(node: ASTNode) = {
    node.getText.trim().length() > 0
  }

  private def mustAlignment(node: ASTNode, s: CodeStyleSettings) = {
    val mySettings = s.getCommonSettings(ScalaFileType.SCALA_LANGUAGE)
    val scalaSettings = s.getCustomSettings(classOf[ScalaCodeStyleSettings])
    node.getPsi match {
      case _: ScXmlStartTag => true  //todo:
      case _: ScXmlEmptyTag => true   //todo:
      case _: ScParameters if mySettings.ALIGN_MULTILINE_PARAMETERS => true
      case _: ScParameterClause if mySettings.ALIGN_MULTILINE_PARAMETERS => true
      case _: ScTemplateParents if mySettings.ALIGN_MULTILINE_EXTENDS_LIST => true
      case _: ScArgumentExprList if mySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS  ||
              mySettings.ALIGN_MULTILINE_METHOD_BRACKETS => true
      case _: ScPatternArgumentList if mySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS ||
              mySettings.ALIGN_MULTILINE_METHOD_BRACKETS => true
      case _: ScEnumerators if mySettings.ALIGN_MULTILINE_FOR => true
      case _: ScParenthesisedExpr if mySettings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION => true
      case _: ScParenthesisedTypeElement if mySettings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION => true
      case _: ScParenthesisedPattern if mySettings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION => true
      case _: ScInfixExpr if mySettings.ALIGN_MULTILINE_BINARY_OPERATION => true
      case _: ScInfixPattern if mySettings.ALIGN_MULTILINE_BINARY_OPERATION => true
      case _: ScInfixTypeElement if mySettings.ALIGN_MULTILINE_BINARY_OPERATION => true
      case _: ScCompositePattern if scalaSettings.ALIGN_COMPOSITE_PATTERN => true

      case _: ScMethodCall | _: ScReferenceExpression if mySettings.ALIGN_MULTILINE_CHAINED_METHODS => true
      case _ => false
    }
  }

  private val INFIX_ELEMENTS = TokenSet.create(ScalaElementTypes.INFIX_EXPR,
    ScalaElementTypes.INFIX_PATTERN,
    ScalaElementTypes.INFIX_TYPE)

}