package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.formatting.{Alignment, Indent, Wrap}
import com.intellij.lang.ASTNode
import com.intellij.psi.codeStyle.{CodeStyleSettings, CommonCodeStyleSettings}
import org.jetbrains.plugins.scala.lang.formatting.processors.ScalaIndentProcessor
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

abstract class ScalaBlockBuilderBase(
  block: ScalaBlock,
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
    val indentFinal = indent.getOrElse(ScalaIndentProcessor.getChildIndent(block, node))
    val wrapFinal = wrap.getOrElse(ScalaWrapManager.arrangeSuggestedWrapForChild(block, node, block.suggestedWrap)(scalaSettings))
    new ScalaBlock(node, lastNode, alignment, indentFinal, wrapFinal, settings, context)
  }
}
