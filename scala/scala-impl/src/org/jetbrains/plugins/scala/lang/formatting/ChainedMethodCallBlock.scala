package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.formatting.{Alignment, Indent, Wrap}
import com.intellij.lang.ASTNode
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.jetbrains.annotations.Nullable

/**
 * Represents a part of method call chain.<br>
 * Example: {{{
 *    myDefinition.myMethodCall1().myMethodCall2()
 *    |---part1--||----part2-----||----part3-----|
 * }}}
 *
 * @param endsWithColonArgsOrBraceOrIndentedCaseClauses true for block `.bar: 42` in {{{
 *                                                        foo.bar:
 *                                                        42
 *                                                      }}}
 *
 *                                                      false for block .bar(42) in {{{
 *                                                        foo.bar(
 *                                                          42
 *                                                        )
 *                                                      }}}
 */
final class ChainedMethodCallBlock(
  parentBlock: ScalaBlock,
  node: ASTNode,
  lastNode: ASTNode,
  @Nullable alignment: Alignment,
  @Nullable indent: Indent,
  @Nullable wrap: Wrap,
  settings: CodeStyleSettings,
  subBlocksContext: Option[SubBlocksContext],
  val endsWithColonArgsOrBraceOrIndentedCaseClauses: Boolean,
  val isInMatchExpr: Boolean
) extends ScalaBlock(
  Some(parentBlock),
  node,
  lastNode,
  alignment,
  indent,
  wrap,
  settings,
  subBlocksContext
) {

  override def getDebugName: String = {
    val suffix1 = if (endsWithColonArgsOrBraceOrIndentedCaseClauses) " (colon arg)" else ""
    val suffix2 = if (isInMatchExpr) " (match)" else ""
    classOf[ChainedMethodCallBlock].getSimpleName + suffix1 + suffix2
  }
}