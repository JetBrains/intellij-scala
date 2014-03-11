package org.jetbrains.plugins.scala
package lang.formatting.automatic

import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import com.intellij.psi.PsiDocumentManager
import com.intellij.formatting.{Indent, WrapType, Wrap}
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.{ScalaFormattingRule, ScalaBlockRule}

/**
 * @author Roman.Shein
 *         Date: 07.11.13
 */
package object settings {

  /**
   * Returns all blocks that are supposed to have the same formatting settings as current block, i.e.
   * all the blocks that match exactly the same ScalaBlockRule as current block.
   * @param block
   * @return
   */
  def getSameTypeNeighbours(block: ScalaBlock): Seq[ScalaBlock] = ???

  def getNoSpaceIndents: List[Indent] = List[Indent](Indent.getNoneIndent, Indent.getNormalIndent,
    Indent.getContinuationIndent, Indent.getNormalIndent(true), Indent.getContinuationIndent(true))

  /**
   * Returns a list of spacing and indent settings that could produce formatting for given block in text.
   * @param block block to deduce possible formatting settings for
   * @param ruleInstance
   * @return list of tuples of Spacing, Indent, Wrap and Alignment settings. Every entry is a possible formattign setting.
   */
  def getPossibleSpacingSettings(block: ScalaBlock, ruleInstance: ScalaFormattingRuleInstance): List[ScalaBlockFormatterEntry] = {
    if (block.getNode.getTreePrev == null && block.myParentBlock != null && block.getLineNumber == block.myParentBlock.getLineNumber) {
      //by default whitespace belongs to the parent, so we act as if we had "" spacing
      List(ScalaBlockFormatterEntry(SpacingInfo(""), IndentInfo(0, true), block, ruleInstance, true))
    } else {
      var res = List[ScalaBlockFormatterEntry]()
      //whitespace belongs to this block, process it as needed
      val whitespace = block.getInitialWhiteSpace
      val hasNewline = whitespace.contains("\n")
      //cases when whitespace is produced by spacing
      if (hasNewline) {
        //the spacing is a bunch of newlines
        val spacing = whitespace.substring(0, whitespace.lastIndexOf("\n") + 1)
        val directIndent = block.getIndentFromDirectParent
        if (directIndent >= 0) {
          res = ScalaBlockFormatterEntry(SpacingInfo(spacing), IndentInfo(block.getIndentFromDirectParent,
            indentRelativeToDirectParent = true), block, ruleInstance) :: res
        }
        block.getIndentFromNewlineAncestor match {
          case Some(indent) if indent >= 0 => res = ScalaBlockFormatterEntry(SpacingInfo(spacing), IndentInfo(indent,
            indentRelativeToDirectParent = false), block, ruleInstance) :: res
          case _ =>
        }
        if (whitespace.endsWith("\n")) {
          //maybe it's just newline spacing
          res = ScalaBlockFormatterEntry(SpacingInfo(whitespace), block, ruleInstance) :: res
        }
      } else {
        //plain spacing
        res = ScalaBlockFormatterEntry(SpacingInfo(whitespace), block, ruleInstance) :: res
      }
      res
    }
  }

}
