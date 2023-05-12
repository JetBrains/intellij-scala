package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.formatting.{Alignment, Indent, Wrap}
import com.intellij.lang.ASTNode
import com.intellij.psi.codeStyle.{CodeStyleSettings, CommonCodeStyleSettings}
import org.jetbrains.plugins.scala.lang.formatting.processors.ScalaIndentProcessor
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

abstract class ScalaBlockBuilderBase(
  parentBlock: ScalaBlock,
  settings: CodeStyleSettings,
  commonSettings: CommonCodeStyleSettings,
  scalaSettings: ScalaCodeStyleSettings
) {

  // shortcuts to simplify long conditions that operate with settings
  @inline protected def cs: CommonCodeStyleSettings = commonSettings
  @inline protected def ss: ScalaCodeStyleSettings = scalaSettings

  protected final def subBlock(
    node: ASTNode,
    lastNode: ASTNode = null,
    alignment: Alignment = null,
    indent: Option[Indent] = None,
    wrap: Option[Wrap] = None,
    context: Option[SubBlocksContext] = None
  ): ScalaBlock = {
    val indentFinal = indent.getOrElse(ScalaIndentProcessor.getChildIndent(parentBlock, node))
    val wrapFinal = wrap.getOrElse(ScalaWrapManager.arrangeSuggestedWrapForChild(parentBlock, node, parentBlock.suggestedWrap)(scalaSettings))
    val block = new ScalaBlock(node, lastNode, alignment, indentFinal, wrapFinal, settings, context)
    block.parentBlock = Some(parentBlock)
    block
  }
}
