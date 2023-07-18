package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.formatting.{Alignment, Indent, Wrap}
import com.intellij.lang.ASTNode
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.jetbrains.annotations.Nullable

/**
 * Represents a part of method call chain. Example: {{{
 *    myDefinition.myMethodCall1().myMethodCall2()
 *    |---part1--||----part2-----||----part3-----|
 * }}}
 */
final class ChainedMethodCallBlock(
  parentBlock: ScalaBlock,
  node: ASTNode,
  lastNode: ASTNode,
  @Nullable alignment: Alignment,
  @Nullable indent: Indent,
  @Nullable wrap: Wrap,
  settings: CodeStyleSettings,
  subBlocksContext: Option[SubBlocksContext] = None
) extends ScalaBlock(
  Some(parentBlock),
  node,
  lastNode,
  alignment,
  indent,
  wrap,
  settings,
  subBlocksContext
)
