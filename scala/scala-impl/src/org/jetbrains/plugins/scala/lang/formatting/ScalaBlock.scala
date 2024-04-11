package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.formatting._
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.codeStyle.{CodeStyleSettings, CommonCodeStyleSettings}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.formatting.processors._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._

import java.util
import scala.annotation.{tailrec, unused}
import scala.jdk.CollectionConverters._

/**
 * @param indent using `var` for `indent` because it's much easier this way when handling method call chains
 *               (see [[ChainedMethodCallsBlockBuilder]]
 */
class ScalaBlock(
  val parentBlock: Option[ScalaBlock],
  val node: ASTNode,
  @Nullable val lastNode: ASTNode,
  @Nullable val alignment: Alignment,
  @Nullable var indent: Indent,
  @Nullable val wrap: Wrap,
  val settings: CodeStyleSettings,
  val subBlocksContext: Option[SubBlocksContext]
) extends ASTBlock with ScalaTokenTypes {

  def this(
    node: ASTNode,
    lastNode: ASTNode,
    @Nullable alignment: Alignment,
    @Nullable indent: Indent,
    @Nullable wrap: Wrap,
    settings: CodeStyleSettings,
    subBlocksContext: Option[SubBlocksContext] = None
  ) = {
    this(None, node, lastNode, alignment, indent, wrap, settings, subBlocksContext)
  }

  protected var subBlocks: util.List[Block] = _

  def commonSettings: CommonCodeStyleSettings = settings.getCommonSettings(ScalaLanguage.INSTANCE)

  override def getNode: ASTNode = node

  override def getTextRange: TextRange =
    if (lastNode == null) node.getTextRange
    else new TextRange(node.getTextRange.getStartOffset, lastNode.getTextRange.getEndOffset)

  @Nullable override def getIndent: Indent = indent

  @Nullable override def getWrap: Wrap = wrap

  @Nullable override def getAlignment: Alignment = alignment

  override def isLeaf: Boolean = isLeaf(node)

  override def isIncomplete: Boolean = ScalaBlock.isIncomplete(if (lastNode != null) lastNode else node)

  override def getChildAttributes(newChildIndex: Int): ChildAttributes = ScalaBlockChildAttributes.getChildAttributes(this, newChildIndex)

  /**
   * Used to pass to intellij logic when scalafmt is enabled.<br>
   * In case there are any changes in IntelliJ formatter settings (even though scalafmt is selected),
   * we do not want these settings to be applicable in `getChildAttributesScalafmtInner`
   *
   * TODO: can't we reuse single instance for the whole file instead of for the each block?
   */
  private[formatting] val DefaultScalaCodeStyleSettings = new ScalaCodeStyleSettings(settings)

  override def getSpacing(child1: Block, child2: Block): Spacing = {
    val spacing = ScalaSpacingProcessor.getSpacing(child1.asInstanceOf[ScalaBlock], child2.asInstanceOf[ScalaBlock])
    // printSubBlocksSpacingDebugInfoToConsole(child1, child2, spacing)
    spacing
  }

  override def getSubBlocks: util.List[Block] = {
    if (subBlocks == null) {
      subBlocks = new ScalaBlockBuilder(this).buildSubBlocks
      subBlocks.removeIf(_.asInstanceOf[ScalaBlock].getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE)
      // printSubBlocksDebugInfoToConsole()
    }
    subBlocks
  }

  def isLeaf(node: ASTNode): Boolean = {
    lastNode == null && node.getFirstChildNode == null
  }

  private var _suggestedWrap: Wrap = _

  def suggestedWrap: Wrap = {
    if (_suggestedWrap == null) {
      _suggestedWrap = ScalaWrapManager.suggestedWrap(this)
    }
    _suggestedWrap
  }

  def getChildBlockContext(childNode: ASTNode): Option[SubBlocksContext] =
    for {
      context <- subBlocksContext
      childContext <- context.childrenAdditionalContexts.get(childNode)
    } yield childContext

  def getChildBlockLastNode(childNode: ASTNode): ASTNode = {
    val childContext = getChildBlockContext(childNode)
    childContext.map(_.lastNode(childNode)).orNull
  }

  def getChildBlockCustomAlignment(childNode: ASTNode): Option[Alignment] = {
    val childContext = getChildBlockContext(childNode)
    childContext.flatMap(_.alignment)
  }


  //noinspection HardCodedStringLiteral
  // use these methods only for debugging
  @unused("debug print utility")
  private def printSubBlocksDebugInfoToConsole(): Unit = {
    println("#########################################")
    println(s"Parent: ${node.getPsi.getClass.getSimpleName} $getTextRange $indent $alignment")
    println(this.debugText)
    println(s"Children: (${if (subBlocks.isEmpty) "<empty>" else subBlocks.size()})")
    subBlocks.asScala.map(_.asInstanceOf[ScalaBlock]).zipWithIndex.foreach { case (child, idx) =>
      println(s"$idx: ${child.debugText}")
      println(s"$idx: ${child.getTextRange} ${child.indent} ${child.alignment} ${child.wrap}")
    }
    println()
  }

  //noinspection HardCodedStringLiteral
  @unused("debug print utility")
  private def printSubBlocksSpacingDebugInfoToConsole(child1: Block, child2: Block, spacing: Spacing): Unit = {
    (child1, child2, spacing) match {
      case (c1: ScalaBlock, c2: ScalaBlock, s: SpacingImpl) =>
        println(
          s"""Spacing:
             |    child1: ${c1.debugText}
             |    child2: ${c2.debugText}
             |    result: $s (${if(s.isReadOnly) "readonly" else ""})
             |""".stripMargin
        )
      case _ =>
    }
  }

  //noinspection HardCodedStringLiteral
  private def debugText: String = {
    import org.jetbrains.plugins.scala.extensions._
    val text = node.getPsi.getContainingFile.getText.substring(getTextRange)
    if (text.trim.length != text.length) s"`$text`"
    else text
  }
}

object ScalaBlock {
  def isConstructorArgOrMemberFunctionParameter(paramClause: ScParameterClause): Boolean = {
    paramClause.owner match {
      case _: ScPrimaryConstructor | _: ScFunction => true
      case _ => false
    }
  }

  @tailrec
  final def isIncomplete(node: ASTNode): Boolean = {
    val psi = node.getPsi
    if (psi.is[PsiErrorElement])
      return true

    val lastChild = findLastNonBlankChild(node)
    if (lastChild == null)
      return false // leaf node

    // mostly required for Enter handler
    val isCurrentIncomplete = psi match {
      // `class A:<caret>`
      case _: ScTemplateBody => lastChild.getElementType == ScalaTokenTypes.tCOLON
      // `given intOrd: Ord[Int] with <caret>`
      case _: ScExtendsBlock => lastChild.getElementType == ScalaTokenTypes.kWITH
      case ret: ScReturn if ret.expr.isEmpty =>
        // NOTE: compare only type text, do not
        val hasUnitReturnType: Boolean = ret.method.exists(_.returnTypeElement.exists(isUnitTypeText))
        !hasUnitReturnType
      case _: ScFunctionExpr =>
        // call: arg => <caret>
        val elementType = lastChild.getElementType
        elementType == ScalaTokenTypes.tFUNTYPE || elementType == ScalaTokenType.ImplicitFunctionArrow
      case _ => false
    }
    if (isCurrentIncomplete)
      true
    else
      isIncomplete(lastChild)
  }

  private def isUnitTypeText(typeElement: ScTypeElement): Boolean = {
    val text = typeElement.getText
    text match {
      case "Unit" | "scala.Unit" | "_root_.scala.Unit" => true
      case _ => false
    }
  }

  @Nullable
  private def findLastNonBlankChild(node: ASTNode): ASTNode = {
    var lastChild = node.getLastChildNode
    while (lastChild != null && isBlank(lastChild.getPsi)) {
      lastChild = lastChild.getTreePrev
    }
    lastChild
  }

  @inline
  private def isBlank(psi: PsiElement): Boolean = psi match {
    // empty template, e.g. in empty `given intOrd: Ord[Int] with <caret>`
    case body: ScTemplateBody             => body.getFirstChild == null
    // e.g. empty case clause body
    case block: ScBlock                   => block.getFirstChild == null
    case _: PsiWhiteSpace | _: PsiComment => true
    case _                                => false
  }
}




