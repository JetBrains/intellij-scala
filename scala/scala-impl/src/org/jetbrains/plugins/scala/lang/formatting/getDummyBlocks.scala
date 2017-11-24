package org.jetbrains.plugins.scala
package lang
package formatting
/**
* @author ilyas
*/

import java.util

import com.intellij.formatting._
import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.tree._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.formatting.ScalaWrapManager._
import org.jetbrains.plugins.scala.lang.formatting.processors._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScModifierListOwner, ScPackaging}
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocTag}
import org.jetbrains.plugins.scala.project.UserDataHolderExt
import org.jetbrains.plugins.scala.util.MultilineStringUtil

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


object getDummyBlocks {
  val fieldGroupAlignmentKey: Key[Alignment] = Key.create("field.group.alignment.key")
  type InterpolatedPointer = SmartPsiElementPointer[ScInterpolatedStringLiteral]

  private val alignmentsMapKey: Key[mutable.Map[InterpolatedPointer, Alignment]] = Key.create("alingnments.map")
  private val multiLevelAlignmentKey: Key[mutable.Map[IElementType, List[ElementPointerAlignmentStrategy]]] = Key.create("multilevel.alignment")

  private def alignmentsMap(project: Project): mutable.Map[InterpolatedPointer, Alignment] = {
    project.getOrUpdateUserData(alignmentsMapKey, mutable.Map[InterpolatedPointer, Alignment]())
  }

  private def cachedAlignment(literal: ScInterpolatedStringLiteral): Option[Alignment] = {
    alignmentsMap(literal.getProject).collectFirst {
      case (pointer, alignment) if pointer.getElement == literal => alignment
    }
  }

  private def multiLevelAlignmentMap(project: Project): mutable.Map[IElementType, List[ElementPointerAlignmentStrategy]] = {
    project.getOrUpdateUserData(multiLevelAlignmentKey,
      mutable.Map[IElementType, List[ElementPointerAlignmentStrategy]]())
  }

  def apply(firstNode: ASTNode, lastNode: ASTNode, block: ScalaBlock): util.ArrayList[Block] =
    if (lastNode != null) applyInner(firstNode, lastNode, block) else applyInner(firstNode, block)


  private def applyInner(node: ASTNode, block: ScalaBlock): util.ArrayList[Block] = {
    val children = node.getChildren(null)
    val subBlocks = new util.ArrayList[Block]
    val settings = block.getCommonSettings
    val scalaSettings = block.getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    node.getPsi match {
      case _: ScValue | _: ScVariable if settings.ALIGN_GROUP_FIELD_DECLARATIONS =>
        if (node.getTreeParent.getPsi match { case _: ScEarlyDefinitions | _: ScTemplateBody => true; case _ => false }) {
          subBlocks.addAll(getFieldGroupSubBlocks(node, block))
          return subBlocks
        }
      case _: ScCaseClause if scalaSettings.ALIGN_IN_COLUMNS_CASE_BRANCH =>
        subBlocks.addAll(getCaseClauseGroupSubBlocks(node, block))
        return subBlocks
      case _: ScIfStmt =>
        val alignment = if (scalaSettings.ALIGN_IF_ELSE) Alignment.createAlignment
                        else null
        subBlocks.addAll(getIfSubBlocks(node, block, alignment))
        return subBlocks
      case _: ScInfixExpr | _: ScInfixPattern | _: ScInfixTypeElement =>
        subBlocks.addAll(getInfixBlocks(node, block))
        return subBlocks
      case _: ScExtendsBlock =>
        subBlocks.addAll(getExtendsSubBlocks(node, block))
        return subBlocks
      case _: ScForStatement =>
        subBlocks.addAll(getForSubBlocks(node, block, children))
        return subBlocks
      case _: ScReferenceExpression =>
        subBlocks.addAll(getMethodCallOrRefExprSubBlocks(node, block))
        return subBlocks
      case _: ScMethodCall =>
        subBlocks.addAll(getMethodCallOrRefExprSubBlocks(node, block))
        return subBlocks
      case _: ScLiteral if node.getFirstChildNode != null &&
              node.getFirstChildNode.getElementType == ScalaTokenTypes.tMULTILINE_STRING &&
              scalaSettings.MULTILINE_STRING_SUPORT != ScalaCodeStyleSettings.MULTILINE_STRING_NONE =>
        subBlocks.addAll(getMultilineStringBlocks(node, block))
        return subBlocks
      case _: ScTryBlock if children.headOption.exists(_.getElementType == ScalaTokenTypes.kTRY) =>
        //add try block
        subBlocks.add(new ScalaBlock(block, children.head, null, null, ScalaIndentProcessor.getChildIndent(block, children.head),
          arrangeSuggestedWrapForChild(block, children.head, scalaSettings, block.suggestedWrap), block.getSettings))
        //add subblock with try expr
        val tail = children.filter(isCorrectBlock).tail
        if (tail.nonEmpty) {
          if (tail.length == 1 && tail.head.isInstanceOf[ScExpression]) {
            //there is a single expr under try
            subBlocks.add(new ScalaBlock(block, tail.head, null, null, ScalaIndentProcessor.getChildIndent(block, tail.head),
              arrangeSuggestedWrapForChild(block, tail.head, scalaSettings, block.suggestedWrap), block.getSettings))
          } else {
            //there is block expr under try
            subBlocks.add(new ScalaBlock(block, tail.head, tail.last, null, ScalaIndentProcessor.getChildIndent(block, tail.head),
              arrangeSuggestedWrapForChild(block, tail.head, scalaSettings, block.suggestedWrap), block.getSettings))
          }
        }
        return subBlocks
      case pack: ScPackaging if pack.isExplicit =>
        val correctChildren = children.filter(isCorrectBlock)
        val (beforeOpenBrace, afterOpenBrace) = correctChildren.span(_.getElementType != ScalaTokenTypes.tLBRACE)
        val hasValidTail = afterOpenBrace.nonEmpty && afterOpenBrace.head.getElementType == ScalaTokenTypes.tLBRACE &&
          afterOpenBrace.last.getElementType == ScalaTokenTypes.tRBRACE
        for (child <- if (hasValidTail) beforeOpenBrace else correctChildren) {
          subBlocks.add(getSubBlock(block, scalaSettings, child,
            indent = ScalaIndentProcessor.getChildIndent(block, child)))
        }
        if (hasValidTail) {
          subBlocks.add(getSubBlock(block, scalaSettings, afterOpenBrace.head, afterOpenBrace.last,
            ScalaIndentProcessor.getChildIndent(block, afterOpenBrace.head)))
        }
        return subBlocks
      case _: ScDocComment =>
        var scalaDocPrevChildTag: Option[String] = None
        var contextAlignment: Alignment = Alignment.createAlignment(true)
        val alignment = if (mustAlignment(node, block.getSettings)) Alignment.createAlignment else null
        for (child <- children if isCorrectBlock(child)) {
          val context = (child.getElementType match {
            case ScalaDocElementTypes.DOC_TAG =>
              val currentTag = Option(child.getFirstChildNode).filter(_.getElementType == ScalaDocTokenType.DOC_TAG_NAME).map(_.getText)
              if (scalaDocPrevChildTag.isEmpty || scalaDocPrevChildTag != currentTag) {
                contextAlignment = Alignment.createAlignment(true)
              }
              scalaDocPrevChildTag = currentTag
              Some(contextAlignment)
            case _ => None
          }).map(a => new SubBlocksContext(alignment = Some(a)))
          subBlocks.add(new ScalaBlock(block, child, null, alignment, ScalaIndentProcessor.getChildIndent(block, child),
            arrangeSuggestedWrapForChild(block, child, scalaSettings, block.suggestedWrap), block.getSettings, context))
        }
        return subBlocks
      case _
        if node.getElementType == ScalaDocElementTypes.DOC_TAG =>
        val docTag = node.getPsi.asInstanceOf[ScDocTag]

        @tailrec
        def getNonWsSiblings(firstNode: ASTNode, acc: List[ASTNode] = List()): List[ASTNode] =
          if (firstNode == null) {
            acc.reverse
          } else if (ScalaDocNewlinedPreFormatProcessor.isWhiteSpace(firstNode)) {
            getNonWsSiblings(firstNode.getTreeNext, acc)
          } else {
            getNonWsSiblings(firstNode.getTreeNext, firstNode :: acc)
          }

        val childBlocks = getNonWsSiblings(docTag.getFirstChild.getNode)
        //TODO whitespace between tag name and tag parameter (like in @param x) has type "DOC_COMMENT_DATA"
        //while it should be DOC_WHITESPACE
        childBlocks match {
          case tagName :: space :: tagParameter :: tail
            if Option(docTag.getValueElement).map(_.getNode).exists(_ == tagParameter) =>
            subBlocks.add(getSubBlock(block, scalaSettings, tagName))
            subBlocks.add(getSubBlock(block, scalaSettings, space))
            subBlocks.add(getSubBlock(block, scalaSettings, tagParameter, if (tail.isEmpty) null else tail.last))
          case tagName :: tail if Option(docTag.getNameElement).map(_.getNode).exists(_ == tagName) =>
            subBlocks.add(getSubBlock(block, scalaSettings, tagName))
            if (tail.nonEmpty) {
              if (tail.head.getElementType != ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS)
                subBlocks.add(getSubBlock(block, scalaSettings, tail.head, tail.last))
              else for (child <- tail) {
                subBlocks.add(getSubBlock(block, scalaSettings, child))
              }
            }
          case _ =>
        }
        return subBlocks
      case interpolated: ScInterpolatedStringLiteral =>
        //create and store alignment; required for support of multi-line interpolated strings (SCL-8665)
        alignmentsMap(interpolated.getProject).put(interpolated.createSmartPointer, Alignment.createAlignment())
      case _: ScValue | _: ScVariable | _: ScFunction if node.getFirstChildNode.getPsi.isInstanceOf[PsiComment] =>
        val childrenFiltered = children.filter(isCorrectBlock)
        subBlocks.add(getSubBlock(block, scalaSettings, childrenFiltered.head))
        val tail = childrenFiltered.tail
        subBlocks.add(new ScalaBlock(block, tail.head, tail.last, null, {
          val ws = Option(node.getTreePrev)
          val preWsET = ws.map(_.getTreePrev).flatMap(Option(_)).map(_.getElementType)
          ws.map(_.getPsi) match {
            case Some(ws: PsiWhiteSpace) if scalaSettings.KEEP_COMMENTS_ON_SAME_LINE &&
              (preWsET.exists(_ == ScalaTokenTypes.tLBRACE) || preWsET.exists(_ == ScalaTokenTypes.tLPARENTHESIS)) &&
              !ws.getText.contains("\n") => Indent.getNormalIndent
            case _ => Indent.getNoneIndent
          }
        }, arrangeSuggestedWrapForChild(block, node, scalaSettings, block.suggestedWrap), block.getSettings))
        return subBlocks
      case _ =>
    }
    val alignment: Alignment = if (mustAlignment(node, block.getSettings))
      Alignment.createAlignment
    else null
    var alternateAlignment: Alignment = null
    for (child <- children if isCorrectBlock(child)) {
      val indent = ScalaIndentProcessor.getChildIndent(block, child)
      val childAlignment: Alignment = {
        node.getPsi match {
          case _: ScParameterClause =>
            child.getElementType match {
              case ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tLPARENTHESIS => null
              case _ => alignment
            }
          case args: ScArgumentExprList =>
            child.getElementType match {
              case ScalaTokenTypes.tRPARENTHESIS if args.missedLastExpr &&
                      settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS => alignment
              case ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tLPARENTHESIS =>
                if (settings.ALIGN_MULTILINE_METHOD_BRACKETS) {
                  if (alternateAlignment == null) {
                    alternateAlignment = Alignment.createAlignment
                  }
                  alternateAlignment
                } else null
              case ScalaElementTypes.BLOCK_EXPR if scalaSettings.DO_NOT_ALIGN_BLOCK_EXPR_PARAMS => null
              case _ if settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS => alignment
              case _ => null
            }
          case patt: ScPatternArgumentList =>
            child.getElementType match {
              case ScalaTokenTypes.tRPARENTHESIS if patt.missedLastExpr &&
                      settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS => alignment
              case ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tLPARENTHESIS =>
                if (settings.ALIGN_MULTILINE_METHOD_BRACKETS) {
                  if (alternateAlignment == null) {
                    alternateAlignment = Alignment.createAlignment
                  }
                  alternateAlignment
                } else null
              case ScalaElementTypes.BLOCK_EXPR if scalaSettings.DO_NOT_ALIGN_BLOCK_EXPR_PARAMS => null
              case _ if settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS => alignment
              case _ => null
            }
          case _: ScMethodCall | _: ScReferenceExpression =>
            if (child.getElementType == ScalaTokenTypes.tIDENTIFIER &&
                    child.getPsi.getParent.isInstanceOf[ScReferenceExpression] &&
                    child.getPsi.getParent.asInstanceOf[ScReferenceExpression].qualifier.isEmpty) null
            else if (child.getPsi.isInstanceOf[ScExpression]) null
            else alignment
          case _: ScXmlStartTag  | _: ScXmlEmptyTag =>
            child.getElementType match {
              case ScalaElementTypes.XML_ATTRIBUTE => alignment
              case _ => null
            }
          case _: ScXmlElement =>
            child.getElementType match {
              case ScalaElementTypes.XML_START_TAG | ScalaElementTypes.XML_END_TAG => alignment
              case _ => null
            }
          case _: ScParameter =>
            child.getElementType match {
              case ScalaTokenTypes.tCOLON if scalaSettings.ALIGN_TYPES_IN_MULTILINE_DECLARATIONS =>
                Option(child.getPsi).flatMap(p => Option(p.getParent)).flatMap(p => Option(p.getParent)).map(rootPsi => {
                  val map = multiLevelAlignmentMap(rootPsi.getProject)
                  map.get(ScalaTokenTypes.tCOLON).flatMap(_.find(_.shouldAlign(child))) match {
                    case Some(multiAlignment) => multiAlignment.getAlignment
                    case None =>
                      val multiAlignment = ElementPointerAlignmentStrategy.typeMultiLevelAlignment(rootPsi)
                      assert(multiAlignment.shouldAlign(child))
                      map.update(ScalaTokenTypes.tCOLON,
                        multiAlignment :: map.getOrElse(ScalaTokenTypes.tCOLON, List()))
                      multiAlignment.getAlignment
                  }
                }).getOrElse(alignment)
              case _ => alignment
            }
          case _ => alignment
        }
      }
      val childWrap = arrangeSuggestedWrapForChild(block, child, scalaSettings, block.suggestedWrap)
      if (child.getFirstChildNode == null && child.getElementType == ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING &&
          scalaSettings.MULTILINE_STRING_SUPORT != ScalaCodeStyleSettings.MULTILINE_STRING_NONE) {
        //flatten interpolated strings
        subBlocks.addAll(getMultilineStringBlocks(child, block))
      } else {
        subBlocks.add(new ScalaBlock(block, child, null, childAlignment, indent, childWrap, block.getSettings))
      }
    }
    subBlocks
  }

  private def applyInner(node: ASTNode, lastNode: ASTNode, block: ScalaBlock): util.ArrayList[Block] = {
    val settings = block.getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val scalaSettings = block.getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val subBlocks = new util.ArrayList[Block]

    def flattenChildren(multilineNode: ASTNode, buffer: ArrayBuffer[ASTNode]) {
      for (nodeChild <- multilineNode.getChildren(null)) {
        if (nodeChild.getText.contains("\n") && nodeChild.getFirstChildNode != null) {
          flattenChildren(nodeChild, buffer)
        } else {
          buffer += nodeChild
        }
      }
    }

    if (ScalaDocTokenType.ALL_SCALADOC_TOKENS.contains(node.getElementType) ||
            (node.getTreeParent != null && node.getTreeParent.getElementType == ScalaDocElementTypes.DOC_TAG &&
                    node.getPsi.isInstanceOf[PsiErrorElement])) {
      val children = ArrayBuffer[ASTNode]()
      var scaladocNode = node.getElementType match {
        case ScalaDocTokenType.DOC_TAG_VALUE_TOKEN =>
          subBlocks.add(new ScalaBlock(block, node, null, null, Indent.getNoneIndent,
            arrangeSuggestedWrapForChild(block, node, scalaSettings, block.suggestedWrap), block.getSettings))
          node.getTreeNext
        case _ => node
      }

      do {
        if (scaladocNode.getText.contains("\n")) {
          flattenChildren(scaladocNode, children)
        } else {
          children += scaladocNode
        }

      } while (scaladocNode != lastNode && (scaladocNode = scaladocNode.getTreeNext, true)._2)

      val normalAlignment = block.myParentBlock.subBlocksContext.flatMap(_.alignment).getOrElse(Alignment.createAlignment(true))

      children.foreach { child =>
        val indent = ScalaIndentProcessor.getChildIndent(block, child)

        if (isCorrectBlock(child)) {
          val firstSibling = node.getTreeParent.getFirstChildNode
          val (childAlignment, childWrap) = if ( node.getTreeParent.getElementType == ScalaDocElementTypes.DOC_TAG &&
                  child.getElementType != ScalaDocTokenType.DOC_WHITESPACE &&
                  child.getElementType != ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS &&
                  child != firstSibling &&
                  firstSibling.getElementType == ScalaDocTokenType.DOC_TAG_NAME &&
                  child.getText.trim().length() > 0) {
          val wrap = Wrap.createWrap(WrapType.NONE, false)
            (firstSibling.getText match {
              case "@param" | "@tparam" => if (scalaSettings.SD_ALIGN_PARAMETERS_COMMENTS) normalAlignment else null
              case "@return" => if (scalaSettings.SD_ALIGN_RETURN_COMMENTS) normalAlignment else null
              case "@throws" => if (scalaSettings.SD_ALIGN_EXCEPTION_COMMENTS) normalAlignment else null
              case _ => if (scalaSettings.SD_ALIGN_OTHER_TAGS_COMMENTS) normalAlignment else null
            }, wrap)
          } else
            (null, arrangeSuggestedWrapForChild(block, child, settings, block.suggestedWrap))

          subBlocks.add(new ScalaBlock(block, child, null, childAlignment, indent, childWrap, block.getSettings))
        }
      }
    } else {
      var child = node

      do {
        val indent = ScalaIndentProcessor.getChildIndent(block, child)
        if (isCorrectBlock(child) && !child.getPsi.isInstanceOf[ScTemplateParents]) {
          val (childAlignment, childWrap) = (block.getCustomAlignment(child).orNull, arrangeSuggestedWrapForChild(block, child, settings, block.suggestedWrap))

          subBlocks.add(new ScalaBlock(block, child, block.getChildBlockLastNode(child), childAlignment, indent,
            childWrap, block.getSettings, block.subBlocksContext.flatMap(_.childrenAdditionalContexts.get(child))))
        } else if (isCorrectBlock(child)) {
          subBlocks.addAll(getTemplateParentsBlocks(child, block))
        }
      } while (child != lastNode && {child = child.getTreeNext; child != null})
    }

    //it is not used right now, but could come in handy later
    block.subBlocksContext.foreach(_.additionalNodes.foreach { additionalNode =>
      val indent = ScalaIndentProcessor.getChildIndent(block, additionalNode)
      val (childAlignment, childWrap) = (block.getCustomAlignment(additionalNode).orNull, arrangeSuggestedWrapForChild(block, additionalNode, settings, block.suggestedWrap))
      subBlocks.add(new ScalaBlock(block, additionalNode, block.getChildBlockLastNode(additionalNode), childAlignment,
        indent, childWrap, block.getSettings))
    })

    subBlocks
  }

  private def getCaseClauseGroupSubBlocks(node: ASTNode, block: ScalaBlock): util.ArrayList[Block] = {
    val children = node.getChildren(null)
    val subBlocks = new util.ArrayList[Block]
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
            case _: ScCaseClause =>
              true
            case _: PsiComment => false
            case _ =>
              breaks += 2
              false
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

  private def getFieldGroupSubBlocks(node: ASTNode, block: ScalaBlock): util.ArrayList[Block] = {
    val children = node.getChildren(null)
    val subBlocks = new util.ArrayList[Block]
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
              nodePsi.isInstanceOf[ScVariableDefinition] =>
              breaks += 2
              false
            case _: ScVariableDefinition | _: ScPatternDefinition if nodePsi.isInstanceOf[ScValueDeclaration] ||
              nodePsi.isInstanceOf[ScValueDeclaration] =>
              breaks += 2
              false
            case _: ScVariable | _: ScValue =>
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
            case _: PsiComment => false
            case _ =>
              breaks += 2
              false
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
      //TODO process rare case of first-line comment before one of the fields  for SCL-10000 here
      subBlocks.add(new ScalaBlock(block, child, null, childAlignment, indent, childWrap, block.getSettings))
      prevChild = child
    }
    subBlocks
  }

  private def getTemplateParentsBlocks(node: ASTNode, block: ScalaBlock): util.ArrayList[Block] = {
    val settings = block.getSettings
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val subBlocks = new util.ArrayList[Block]
    val children = node.getChildren(null)
    val alignSetting = scalaSettings.ALIGN_EXTENDS_WITH
    import ScalaCodeStyleSettings._
    val alignment = if (alignSetting == ALIGN_TO_EXTENDS) block.getAlignment else Alignment.createAlignment(true)
    for (child <- children) {
      if (isCorrectBlock(child)) {
        val indent = ScalaIndentProcessor.getChildIndent(block, child)
        val childWrap = arrangeSuggestedWrapForChild(block, child, scalaSettings, block.suggestedWrap)
        val actualAlignment = child.getElementType match {
          case _ if alignSetting == DO_NOT_ALIGN => null
          case ScalaTokenTypes.kWITH | ScalaTokenTypes.kEXTENDS =>
            if (alignSetting != ON_FIRST_ANCESTOR) alignment else null
          case _ => alignment
        }
        subBlocks.add(new ScalaBlock(block, child, block.getChildBlockLastNode(child), actualAlignment, indent, childWrap,
          settings, block.subBlocksContext.flatMap(_.childrenAdditionalContexts.get(child))))
      }
    }
    subBlocks
  }

  private def getExtendsSubBlocks(node: ASTNode, block: ScalaBlock): util.ArrayList[Block] = {
    val settings = block.getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val subBlocks = new util.ArrayList[Block]
    val extBlock: ScExtendsBlock = node.getPsi.asInstanceOf[ScExtendsBlock]
    if (extBlock.getFirstChild == null) return subBlocks
    val tempBody = extBlock.templateBody
    val first = extBlock.getFirstChild
    val last = tempBody match {
      case None => extBlock.getLastChild
      case Some(x) =>
        val p = x.getPrevSibling
        if (p.isInstanceOf[PsiWhiteSpace]) p.getPrevSibling else p
    }
    if (last != null) {
      val indent = ScalaIndentProcessor.getChildIndent(block, first.getNode)
      val childWrap = arrangeSuggestedWrapForChild(block, first.getNode, settings, block.suggestedWrap)
      val alignment = if (settings.ALIGN_EXTENDS_WITH == ScalaCodeStyleSettings.ALIGN_TO_EXTENDS) Alignment.createAlignment(false) else null
      subBlocks.add(new ScalaBlock(block, first.getNode, last.getNode, alignment, indent, childWrap, block.getSettings))
    }

    tempBody match {
      case Some(x) =>
        val indent = ScalaIndentProcessor.getChildIndent(block, x.getNode)
        val childWrap = arrangeSuggestedWrapForChild(block, x.getNode, settings, block.suggestedWrap)
        subBlocks.add(new ScalaBlock(block, x.getNode, null, null, indent, childWrap, block.getSettings))
      case _ =>
    }
    subBlocks
  }

  private def getForSubBlocks(node: ASTNode, block: ScalaBlock, children: Array[ASTNode]): util.ArrayList[Block] = {
    var prevChild: ASTNode = null
    val subBlocks = new util.ArrayList[Block]()
    val scalaSettings = block.getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    def addSubBlock(node: ASTNode, lastNode: ASTNode): Unit = {
      subBlocks.add(new ScalaBlock(block, node, lastNode, null, ScalaIndentProcessor.getChildIndent(block, node),
        arrangeSuggestedWrapForChild(block, node, scalaSettings, block.suggestedWrap), block.getSettings))
    }
    def addTail(tail: List[ASTNode]): Unit = {
      for (child <- tail) {
        if (child.getElementType != ScalaTokenTypes.kYIELD) {
          if (prevChild != null && prevChild.getElementType == ScalaTokenTypes.kYIELD) {
            addSubBlock(prevChild, child)
          } else {
            addSubBlock(child, null)
          }
        }
        prevChild = child
      }
      if (prevChild != null && prevChild.getElementType == ScalaTokenTypes.kYIELD) {
        //add a block for 'yield' in case of incomplete for statement (expression after yield is missing)
        addSubBlock(prevChild, null)
      }
    }
    @tailrec
    def addFor(children: List[ASTNode]): Unit = children match {
      case forWord::tail if forWord.getElementType == ScalaTokenTypes.kFOR =>
        addSubBlock(forWord, null)
        addFor(tail)
      case lParen::tail if lParen.getElementType == ScalaTokenTypes.tLPARENTHESIS ||
        lParen.getElementType == ScalaTokenTypes.tLBRACE =>
        val closingType =
          if (lParen.getElementType == ScalaTokenTypes.tLPARENTHESIS) ScalaTokenTypes.tRPARENTHESIS else ScalaTokenTypes.tRBRACE
        val (_, after) =
          tail.span(elem => elem.getElementType != closingType)
        if (after.isEmpty) {
          addTail(children)
        } else {
          addSubBlock(lParen, after.head)
          addTail(after.tail)
        }
      case _ =>
        addTail(children)
    }
    addFor(children.filter(isCorrectBlock).toList)
    subBlocks
  }

  private def getIfSubBlocks(node: ASTNode, block: ScalaBlock, alignment: Alignment): util.ArrayList[Block] = {
    val settings = block.getCommonSettings
    val scalaSettings = block.getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val subBlocks = new util.ArrayList[Block]
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
          case _: ScIfStmt if settings.SPECIAL_ELSE_IF_TREATMENT =>
            val childWrap = arrangeSuggestedWrapForChild(block, firstChild, scalaSettings, block.suggestedWrap)
            subBlocks.add(new ScalaBlock(block, firstChild, child, alignment, indent, childWrap, block.getSettings))
            subBlocks.addAll(getIfSubBlocks(child.getTreeNext, block, alignment))
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

  private def getMultilineStringBlocks(node: ASTNode, block: ScalaBlock): util.ArrayList[Block] = {
    def interpolatedRefLength(node: ASTNode): Int = {
      if (node.getElementType == ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING) {
        node.getPsi().getParent match {
          case l: ScInterpolatedStringLiteral => l.reference.map(_.refName.length).getOrElse(0)
          case _ => 0
        }
      } else 0
    }
    val settings = block.getSettings
    val subBlocks = new util.ArrayList[Block]

    val alignment = null
    val interpolatedOpt = Option(PsiTreeUtil.getParentOfType(node.getPsi, classOf[ScInterpolatedStringLiteral]))
    val validAlignment = interpolatedOpt
      .flatMap(cachedAlignment)
      .getOrElse(Alignment.createAlignment(true))
    val wrap: Wrap = Wrap.createWrap(WrapType.NONE, true)
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val marginChar = "" + MultilineStringUtil.getMarginChar(node.getPsi)
    val marginIndent = scalaSettings.MULTI_LINE_STRING_MARGIN_INDENT

    val indent = Indent.getNoneIndent
    val simpleIndent = Indent.getAbsoluteNoneIndent
    val prefixIndent = Indent.getSpaceIndent(marginIndent + interpolatedRefLength(node), true)

    val lines = node.getText.split("\n")
    var acc = 0



    lines foreach { line =>
      val trimmedLine = line.trim()
      val linePrefixLength = if (settings useTabCharacter ScalaFileType.INSTANCE) {
        val tabsCount = line.prefixLength(_ == '\t')
        tabsCount /* *settings.getTabSize(ScalaFileType.INSTANCE)*/ + line.substring(tabsCount).prefixLength(_ == ' ')
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
        val (startOffset, endOffset, myIndent, myAlignment) = if (trimmedLine.startsWith("\"\"\"") && acc != 0)
          (node.getStartOffset + acc + linePrefixLength, node.getStartOffset + acc + line.length,
              Indent.getSpaceIndent(0, true), alignment)
        else if (trimmedLine.startsWith("\"\"\"") && acc == 0) {
          if (trimmedLine.startsWith("\"\"\"|") && line.length > 3) {
            //split beginning of interpolated string (s"""|<string>) to facilitate alignment in difficult cases
            // first, add block for opening quotes
            subBlocks.add(new StringLineScalaBlock(new TextRange(node.getStartOffset, node.getStartOffset + 3), node,
              block, alignment, Indent.getNoneIndent, null, settings))
            //now, return block parameters for text after the opening quotes
            (node.getStartOffset + 3, node.getStartOffset + line.length, Indent.getNoneIndent, validAlignment)
          } else
            (node.getStartOffset, node.getStartOffset + line.length, Indent.getNoneIndent, alignment)
        } else (node.getStartOffset + acc, node.getStartOffset + acc + line.length, simpleIndent, alignment)

        subBlocks.add(new StringLineScalaBlock(new TextRange(startOffset, endOffset), node, block, myAlignment,
          myIndent, null, settings))
      }

      acc += line.length + 1
    }

    subBlocks
  }

  private def getInfixBlocks(node: ASTNode, block: ScalaBlock, parentAlignment: Alignment = null): util.ArrayList[Block] = {
    val settings = block.getSettings
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val subBlocks = new util.ArrayList[Block]
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
          case inf: ScInfixExpr => priority(inf.operation.getText, assignments = true)
          case inf: ScInfixPattern => priority(inf.reference.getText, assignments = false)
          case inf: ScReferenceableInfixTypeElement => priority(inf.reference.getText, assignments = false)
          case _ => 0
        }
        val parentPriority = node.getPsi match {
          case inf: ScInfixExpr => priority(inf.operation.getText, assignments = true)
          case inf: ScInfixPattern => priority(inf.reference.getText, assignments = false)
          case inf: ScReferenceableInfixTypeElement => priority(inf.reference.getText, assignments = false)
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

  def getMethodCallOrRefExprSubBlocks(node: ASTNode, block: ScalaBlock,
                                      delegatedChildren: List[ASTNode] = List()): util.ArrayList[Block] = {
    val settings = block.getSettings
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val subBlocks = new util.ArrayList[Block]
    val alignment = if (mustAlignment(node, settings))
      Alignment.createAlignment
    else null
    val elementTypes: Set[IElementType] = Set(ScalaElementTypes.METHOD_CALL, ScalaElementTypes.REFERENCE_EXPRESSION)
    def extractParentAlignment: Option[Alignment] =
      Option(block.myParentBlock).map(_.getNode.getElementType) match {
        case Some(elemType) if elementTypes.contains(elemType) => //chained method call, extract alignments
          block.myParentBlock.getSubBlocks.asScala.toList match {
            case _ :: (dotToArgsBlock: ScalaBlock) :: Nil
              if dotToArgsBlock.getNode.getElementType == ScalaTokenTypes.tDOT =>
              Some(dotToArgsBlock.getAlignment)
            case _ => None
          }
        case _ => None
      }
    def addSubBlock(node: ASTNode, lastNode: ASTNode, alignment: Alignment, context: Option[SubBlocksContext] = None): Unit = {
      val indent = ScalaIndentProcessor.getChildIndent(block, node)
      val wrap = arrangeSuggestedWrapForChild(block, node, scalaSettings, block.suggestedWrap)
      subBlocks.add(new ScalaBlock(block, node, lastNode, alignment, indent, wrap, settings, context))
    }

    val children = node.getChildren(null).filter(isCorrectBlock).toList
    val dotAlignment = extractParentAlignment.getOrElse(alignment)
    children match {
      //don't check for element types other then absolutely required - they do not matter
      case caller :: args :: Nil if args.getPsi.isInstanceOf[ScArgumentExprList] =>
        subBlocks.addAll(getMethodCallOrRefExprSubBlocks(caller, block, args :: delegatedChildren))
      case expr :: dot :: id :: Nil  if dot.getElementType == ScalaTokenTypes.tDOT =>
        addSubBlock(expr, null, alignment = null)
        addSubBlock(dot, (id::delegatedChildren).sortBy(_.getTextRange.getStartOffset).lastOption.orNull,
          dotAlignment, Some(SubBlocksContext(id, dotAlignment, delegatedChildren)))
      case expr :: typeArgs :: Nil if typeArgs.getPsi.isInstanceOf[ScTypeArgs] =>
        addSubBlock(expr, (typeArgs::delegatedChildren).sortBy(_.getTextRange.getStartOffset).lastOption.orNull,
          dotAlignment, Some(SubBlocksContext(typeArgs, dotAlignment, delegatedChildren)))
      case expr :: Nil =>
        addSubBlock(expr, delegatedChildren.sortBy(_.getTextRange.getStartOffset).lastOption.orNull,
          dotAlignment, Some(SubBlocksContext(expr, alignment, delegatedChildren)))

      case _ =>
        for (child <- (children ++ delegatedChildren).filter(isCorrectBlock)) {
          addSubBlock(child, null, dotAlignment)
        }
    }
    subBlocks
  }

  private def isCorrectBlock(node: ASTNode) = {
    node.getText.trim().length() > 0
  }

  private def mustAlignment(node: ASTNode, s: CodeStyleSettings) = {
    val mySettings = s.getCommonSettings(ScalaLanguage.INSTANCE)
    val scalaSettings = s.getCustomSettings(classOf[ScalaCodeStyleSettings])
    node.getPsi match {
      case _: ScXmlStartTag => true  //todo:
      case _: ScXmlEmptyTag => true   //todo:
      case _: ScParameters if mySettings.ALIGN_MULTILINE_PARAMETERS => true
      case _: ScParameterClause if mySettings.ALIGN_MULTILINE_PARAMETERS => true
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
    override def getTextRange: TextRange = myTextRange

    override def isLeaf = true

    override def isLeaf(node: ASTNode): Boolean = true

    override def getChildAttributes(newChildIndex: Int): ChildAttributes =
      new ChildAttributes(Indent.getNoneIndent, null)

    override def getSubBlocks: util.List[Block] = {
      if (mySubBlocks == null) {
        mySubBlocks = new util.ArrayList[Block]()
      }
      mySubBlocks
    }

    override def getSpacing(child1: Block, child2: Block): Spacing = Spacing.getReadOnlySpacing
  }

  def getSubBlock(block: ScalaBlock, scalaSettings: ScalaCodeStyleSettings, node: ASTNode, lastNode: ASTNode = null,
                  indent: Indent = Indent.getNoneIndent) =
    new ScalaBlock(block, node, lastNode, null, ScalaIndentProcessor.getChildIndent(block, node),
      arrangeSuggestedWrapForChild(block, node, scalaSettings, block.suggestedWrap), block.getSettings)
}