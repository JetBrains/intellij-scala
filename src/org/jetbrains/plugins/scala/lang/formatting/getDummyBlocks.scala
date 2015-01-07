package org.jetbrains.plugins.scala
package lang
package formatting
/**
* @author ilyas
*/

import _root_.java.util.{ArrayList, List}

import com.intellij.formatting._
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.tree._
import com.intellij.psi.{PsiComment, PsiElement, PsiErrorElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.lang.formatting.ScalaWrapManager._
import org.jetbrains.plugins.scala.lang.formatting.processors._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScModifierListOwner}
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocTag
import org.jetbrains.plugins.scala.util.MultilineStringUtil

import scala.collection.mutable.ArrayBuffer


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
      case _: ScLiteral if node.getFirstChildNode != null &&
              node.getFirstChildNode.getElementType == ScalaTokenTypes.tMULTILINE_STRING &&
              scalaSettings.MULTILINE_STRING_SUPORT != ScalaCodeStyleSettings.MULTILINE_STRING_NONE =>
        subBlocks.addAll(getMultilineStringBlocks(node, block))
        return subBlocks
      case _
        if node.getElementType == ScalaDocElementTypes.DOC_TAG =>
        val docTag = node.getPsi.asInstanceOf[ScDocTag]
        val tagConcernedNode = if (docTag.getValueElement != null) docTag.getValueElement.getNode else
          if (docTag.getNameElement != null) docTag.getNameElement.getNode else null

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
    val alignment = if (mustAlignment(node, block.getSettings)) Alignment.createAlignment else null
    var alternateAlignment: Alignment = null
    for (child <- children if isCorrectBlock(child)) {
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
              case ScalaElementTypes.BLOCK_EXPR if scalaSettings.DO_NOT_ALIGN_BLOCK_EXPR_PARAMS => null
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
              case ScalaElementTypes.BLOCK_EXPR if scalaSettings.DO_NOT_ALIGN_BLOCK_EXPR_PARAMS => null
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

    def flattenChildren(multilineNode: ASTNode, buffer: ArrayBuffer[ASTNode]) {
      for (nodeChild <- multilineNode.getChildren(null)) {
        if (nodeChild.getText.contains("\n") && nodeChild.getFirstChildNode != null) {
          flattenChildren(nodeChild, buffer)
        } else {
          buffer += nodeChild
        }
      }
    }

    val normalAligment = Alignment.createAlignment(true)

    if (ScalaDocTokenType.ALL_SCALADOC_TOKENS.contains(node.getElementType) ||
            (node.getTreeParent != null && node.getTreeParent.getElementType == ScalaDocElementTypes.DOC_TAG &&
                    node.getPsi.isInstanceOf[PsiErrorElement])) {
      val children = ArrayBuffer[ASTNode]()
      var scaladocNode = node

      do {
        if (scaladocNode.getText.contains("\n")) {
          flattenChildren(scaladocNode, children)
        } else {
          children += scaladocNode
        }

      } while (scaladocNode != lastNode && (scaladocNode = scaladocNode.getTreeNext, true)._2);


      children.foreach { child =>
        val indent = ScalaIndentProcessor.getChildIndent(block, child)

        if (isCorrectBlock(child)) {
          val (childAlignment, childWrap) = if ( node.getTreeParent.getElementType == ScalaDocElementTypes.DOC_TAG &&
                  child.getElementType != ScalaDocTokenType.DOC_WHITESPACE &&
                  child.getElementType != ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS &&
                  child.getText.trim().length() > 0)
            (normalAligment, Wrap.createWrap(WrapType.NONE, false)) else
            (null, arrangeSuggestedWrapForChild(block, child, settings, block.suggestedWrap))

          subBlocks.add(new ScalaBlock(block, child, null, childAlignment, indent, childWrap, block.getSettings))
        }
      }
    } else {
      var child = node

      do {
        val indent = ScalaIndentProcessor.getChildIndent(block, child)
        if (isCorrectBlock(child) && !child.getPsi.isInstanceOf[ScTemplateParents]) {
          val (childAlignment, childWrap) = (null, arrangeSuggestedWrapForChild(block, child, settings, block.suggestedWrap))

          subBlocks.add(new ScalaBlock(block, child, null, childAlignment, indent, childWrap, block.getSettings))
        } else if (isCorrectBlock(child)) {
          subBlocks.addAll(getTemplateParentsBlocks(child, block))
        }
      } while (child != lastNode && {child = child.getTreeNext; true})
    }


    subBlocks
  }

  private def getCaseClauseGroupSubBlocks(node: ASTNode, block: ScalaBlock): ArrayList[Block] = {
    val children = node.getChildren(null)
    val subBlocks = new ArrayList[Block]
    var prevChild: ASTNode = null
    val scalaSettings = block.getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    for (child <- children if isCorrectBlock(child)) {
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
    for (child <- children if isCorrectBlock(child)) {
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
    for (child <- children) {
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

  private def getMultilineStringBlocks(node: ASTNode, block: ScalaBlock): ArrayList[Block] = {
    val settings = block.getSettings
    val subBlocks = new ArrayList[Block]

    val alignment = null
    val validAlignment = Alignment.createAlignment(true)
    val wrap: Wrap = Wrap.createWrap(WrapType.NONE, true)
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val marginChar = "" + MultilineStringUtil.getMarginChar(node.getPsi)
    val marginIndent = scalaSettings.MULTI_LINE_STRING_MARGIN_INDENT

    val indent = Indent.getNoneIndent
    val simpleIndent = Indent.getAbsoluteNoneIndent
    val prefixIndent = Indent.getSpaceIndent(marginIndent, true)

    val lines = node.getText.split("\n")
    var acc = 0



    lines foreach { line =>
      val trimmedLine = line.trim()
      val linePrefixLength = if (settings useTabCharacter ScalaFileType.SCALA_FILE_TYPE) {
        val tabsCount = line.prefixLength(_ == '\t')
        tabsCount/* *settings.getTabSize(ScalaFileType.SCALA_FILE_TYPE)*/ + line.substring(tabsCount).prefixLength(_ == ' ')
      } else {
        line.prefixLength(_ == ' ')
      }

      if (trimmedLine.startsWith(marginChar)) {
        subBlocks.add(new StringLineScalaBlock(new TextRange(node.getStartOffset + acc + linePrefixLength,
          node.getStartOffset + acc + linePrefixLength + 1), node, block, validAlignment, prefixIndent, null, settings))
        if (line.length > linePrefixLength + 2 && line.charAt(linePrefixLength + 1) == ' ' ||
                line.length > linePrefixLength + 1 && line.charAt(linePrefixLength + 1) !=  ' ') {
          val suffixOffset = if (line.charAt(linePrefixLength + 1) == ' ') 2 else 1

          subBlocks.add(new StringLineScalaBlock(new TextRange(node.getStartOffset + acc + linePrefixLength + suffixOffset,
            node.getStartOffset + acc + line.length), node, block, null, indent, wrap, settings))
        }
      } else if (trimmedLine.length > 0) {
        val (startOffset, endOffset, myIndent) = if (trimmedLine.startsWith("\"\"\"") && acc != 0)
          (node.getStartOffset + acc + linePrefixLength, node.getStartOffset + acc + line.length, Indent.getSpaceIndent(0, true))
        else if (trimmedLine.startsWith("\"\"\"") && acc == 0) (node.getStartOffset, node.getStartOffset + line.length, Indent.getNoneIndent)
        else (node.getStartOffset + acc, node.getStartOffset + acc + line.length, simpleIndent)

        subBlocks.add(new StringLineScalaBlock(new TextRange(startOffset, endOffset), node, block, alignment,
          myIndent, null, settings))
      }

      acc += line.length + 1
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
    for (child <- children) {
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
    for (child <- children) {
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


  private class StringLineScalaBlock(myTextRange: TextRange, mainNode: ASTNode, myParentBlock: ScalaBlock,
                                     myAlignment: Alignment, myIndent: Indent, myWrap: Wrap, mySettings: CodeStyleSettings)
          extends ScalaBlock(myParentBlock, mainNode, null, myAlignment, myIndent, myWrap, mySettings) {
    override def getTextRange = myTextRange

    override def isLeaf = true

    override def isLeaf(node: ASTNode): Boolean = true

    override def getChildAttributes(newChildIndex: Int): ChildAttributes =
      new ChildAttributes(Indent.getNoneIndent, null)

    override def getSubBlocks(): List[Block] = {
      if (mySubBlocks == null) {
        mySubBlocks = new ArrayList[Block]()
      }
      mySubBlocks
    }

    override def getSpacing(child1: Block, child2: Block) = Spacing.getReadOnlySpacing
  }
}