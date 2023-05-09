package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.formatting.Alignment.createAlignment
import com.intellij.formatting._
import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.codeStyle.{CodeStyleSettings, CommonCodeStyleSettings}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.getDummyBlocksUtils._
import org.jetbrains.plugins.scala.lang.formatting.processors._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocListItem, ScDocTag}

import java.util
import scala.collection.mutable.ArrayBuffer

private object getDummyBlocksScalaDoc {

  def isScalaDocNode(node: ASTNode): Boolean = {
    def isInsideIncompleteScalaDocTag = {
      val parent = node.getTreeParent
      parent != null && parent.getElementType == ScalaDocElementTypes.DOC_TAG &&
        node.getPsi.is[PsiErrorElement]
    }

    node.getElementType == ScalaDocElementTypes.SCALA_DOC_COMMENT ||
      ScalaDocElementTypes.AllElementAndTokenTypes.contains(node.getElementType) ||
      isInsideIncompleteScalaDocTag
  }
}

private class getDummyBlocksScalaDoc(
  block: ScalaBlock,
  settings: CodeStyleSettings,
  private implicit val scalaSettings: ScalaCodeStyleSettings
) {
  // shortcuts to simplify long conditions that operate with settings
  @inline private def ss = scalaSettings

  def applyInnerScaladoc(node: ASTNode): util.ArrayList[Block] = {
    val subBlocks = new util.ArrayList[Block]

    val nodePsi = node.getPsi
    nodePsi match {
      case _: ScDocComment =>
        addScalaDocCommentSubBlocks(node, subBlocks)
      case docTag: ScDocTag =>
        addScalaDocTagSubBlocks(docTag, subBlocks)
      case _ =>
        val sharedAlignment = createAlignmentFor(node)
        val children = node.getChildren(null)
        for (child <- children if isNotEmptyDocNode(child)) {
          val childAlignment = calcChildAlignment(node, child, sharedAlignment)
          subBlocks.add(subBlock(child, null, childAlignment))
        }
    }

    subBlocks
  }

  private def createAlignmentFor(node: ASTNode): Alignment = {
    node.getPsi match {
      case _: ScDocListItem if ss.SD_ALIGN_LIST_ITEM_CONTENT => createAlignment(true)
      case _ => null
    }
  }

  private def calcChildAlignment(parent: ASTNode, child: ASTNode, sharedAlignment: Alignment): Alignment =
    parent.getPsi match {
      case _: ScDocListItem if scalaSettings.SD_ALIGN_LIST_ITEM_CONTENT =>
        val doNotAlignInListItem = child.getElementType match {
          case ScalaDocTokenType.DOC_LIST_ITEM_HEAD |
               ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS |
               ScalaDocTokenType.DOC_WHITESPACE |
               ScalaDocTokenType.DOC_INNER_CODE_TAG |
               ScalaDocElementTypes.DOC_LIST => true
          case _ => false
        }
        if (doNotAlignInListItem) null
        else sharedAlignment
      case _ =>
        sharedAlignment
    }

  private def addScalaDocCommentSubBlocks(docCommentNode: ASTNode, subBlocks: util.ArrayList[Block]): Unit = {
    val node = docCommentNode
    val alignment = createAlignmentFor(node)

    var prevTagName: Option[String] = None
    var lastTagContextAlignment: Alignment = Alignment.createAlignment(true)

    for (child <- node.getChildren(null) if isNotEmptyDocNode(child)) {
      val tagContextAlignment = child.getElementType match {
        case ScalaDocElementTypes.DOC_TAG =>
          val tagName = child.getFirstChildNode.withTreeNextNodes.find(_.getElementType == ScalaDocTokenType.DOC_TAG_NAME).map(_.getText)
          if (prevTagName.isEmpty || prevTagName != tagName) {
            lastTagContextAlignment = Alignment.createAlignment(true)
          }
          prevTagName = tagName
          Some(lastTagContextAlignment)
        case _ => None
      }

      val context = tagContextAlignment.map(a => new SubBlocksContext(alignment = Some(a)))
      subBlocks.add(subBlock(child, null, alignment, context = context))
    }
  }

  private def addScalaDocTagSubBlocks(docTag: ScDocTag, subBlocks: util.ArrayList[Block]): Unit = {
    import ScalaDocTokenType._

    val children = docTag.getNode.getFirstChildNode.withTreeNextNodes.toList
    val (childrenLeading, childrenFromNameElement) =
      children.span(_.getElementType != ScalaDocTokenType.DOC_TAG_NAME)

    /**
     * tag can start not from name element, this can happen e.g. when asterisks
     * is added in [[org.jetbrains.plugins.scala.lang.formatting.processors.ScalaDocNewlinedPreFormatProcessor]]
     * also it can contain leading white space
     */
    childrenLeading.foreach { c =>
      if (isNotEmptyDocNode(c))
        subBlocks.add(subBlock(c))
    }

    childrenFromNameElement match {
      case tagName :: _ :: tagParameter :: tail
        if Option(docTag.getValueElement).exists(_.getNode == tagParameter) =>

        subBlocks.add(subBlock(tagName))
        subBlocks.add(subBlock(tagParameter, tail.lastOption.orNull))
      case tagName :: tail =>
        subBlocks.add(subBlock(tagName))

        if (tail.nonEmpty) {
          val (leadingAsterisks, other) = tail
            .filter(isNotEmptyDocNode)
            .span(_.getElementType == DOC_COMMENT_LEADING_ASTERISKS)
          leadingAsterisks.foreach { c =>
            subBlocks.add(subBlock(c))
          }
          if (other.nonEmpty)
            subBlocks.add(subBlock(other.head, other.last))
        }
      case _ =>
    }
  }

  def applyInnerScaladoc(node: ASTNode, lastNode: ASTNode): util.ArrayList[Block] = {
    val subBlocks = new util.ArrayList[Block]

    val parent = node.getTreeParent

    var scaladocNode = node.getElementType match {
      case ScalaDocTokenType.DOC_TAG_VALUE_TOKEN =>
        subBlocks.add(subBlock(node, indent = Some(Indent.getNoneIndent)))
        node.getTreeNext
      case _ =>
        node
    }

    val children = ArrayBuffer[ASTNode]()
    do {
      if (needFlattenDocElementChildren(scaladocNode)) {
        flattenChildren(scaladocNode, children)
      } else {
        children += scaladocNode
      }
    } while (scaladocNode != lastNode && { scaladocNode = scaladocNode.getTreeNext; true })

    val normalAlignment = {
      val alignmentFromParentContext = block.parentBlock.subBlocksContext.flatMap(_.alignment)
      alignmentFromParentContext.getOrElse(Alignment.createAlignment(true))
    }

    children.view.filter(isNotEmptyDocNode).foreach { child =>
      import ScalaDocTokenType._

      val childType = child.getElementType

      val isDataInsideDocTag: Boolean =
        parent.getElementType == ScalaDocElementTypes.DOC_TAG && (childType match {
          case DOC_WHITESPACE | DOC_COMMENT_LEADING_ASTERISKS | DOC_TAG_NAME => false
          case _ => true
        })

      val (childAlignment, childWrap) =
        if (isDataInsideDocTag) {
          val tagElement = parent.getPsi.asInstanceOf[ScDocTag]
          val tagNameElement = tagElement.getNameElement
          val tagName = tagNameElement.getText

          val alignment = childType match {
            case DOC_INNER_CODE |
                 DOC_INNER_CLOSE_CODE_TAG |
                 DOC_INNER_CODE_TAG |
                 ScalaDocElementTypes.DOC_LIST => null
            case _ =>
              tagName match {
                case "@param" | "@tparam" => if (ss.SD_ALIGN_PARAMETERS_COMMENTS) normalAlignment else null
                case "@return"            => if (ss.SD_ALIGN_RETURN_COMMENTS) normalAlignment else null
                case "@throws"            => if (ss.SD_ALIGN_EXCEPTION_COMMENTS) normalAlignment else null
                case _                    => if (ss.SD_ALIGN_OTHER_TAGS_COMMENTS) normalAlignment else null
              }
          }
          val noWrap = Wrap.createWrap(WrapType.NONE, false)
          (alignment, noWrap)
        }
        else {
          (null, ScalaWrapManager.arrangeSuggestedWrapForChild(block, child, block.suggestedWrap))
        }

      subBlocks.add(subBlock(child, null, childAlignment, wrap = Some(childWrap)))
    }

    subBlocks
  }

  private def needFlattenDocElementChildren(node: ASTNode): Boolean = {
    val check1 = node.getElementType match {
      case ScalaDocElementTypes.DOC_PARAGRAPH => true
      case ScalaDocElementTypes.DOC_LIST => false
      case _ => node.textContains('\n')
    }
    check1 && node.getFirstChildNode != null
  }

  private def flattenChildren(multilineNode: ASTNode, buffer: ArrayBuffer[ASTNode]): Unit =
    for (nodeChild <- multilineNode.getChildren(null))
      if (needFlattenDocElementChildren(nodeChild))
        flattenChildren(nodeChild, buffer)
      else
        buffer += nodeChild

  private def subBlock(
    node: ASTNode,
    lastNode: ASTNode = null,
    alignment: Alignment = null,
    indent: Option[Indent] = None,
    wrap: Option[Wrap] = None,
    context: Option[SubBlocksContext] = None
  ): ScalaBlock = {
    val indentFinal = indent.getOrElse(ScalaIndentProcessor.getChildIndent(block, node))
    val wrapFinal = wrap.getOrElse(ScalaWrapManager.arrangeSuggestedWrapForChild(block, node, block.suggestedWrap))
    new ScalaBlock(block, node, lastNode, alignment, indentFinal, wrapFinal, settings, context)
  }
}
