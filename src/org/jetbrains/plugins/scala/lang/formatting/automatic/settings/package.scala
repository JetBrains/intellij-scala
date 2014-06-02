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

}
