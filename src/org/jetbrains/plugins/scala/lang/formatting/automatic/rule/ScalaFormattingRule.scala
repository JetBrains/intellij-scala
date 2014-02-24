package org.jetbrains.plugins.scala
package lang.formatting.automatic.rule

import com.intellij.formatting._
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import scala.collection.JavaConversions._
import scala.Some
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.IndentType.IndentType
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.{ScalaFormattingRuleMatcher, FormattingSettings, RuleParentInfo}

/**
 * @author Roman.Shein
 *         Date: 09.09.13
 */
trait ScalaFormattingRule {

  val id: String

  /**
   * Check whether the given sequence of blocks complies with the rule.
   * @param blocks
   * @return
   */
  def check(blocks: List[Block], parentAndPosition:Option[RuleParentInfo], top: ScalaFormattingRule, matcher: ScalaFormattingRuleMatcher): Option[RuleMatch] = checkSome(blocks, parentAndPosition, top, matcher) match {
    case Some((before, found, after)) /*if before.size == 0 && after.size == 0*/ => Some(found)
    case _ => None
  }

  def check(block: ScalaBlock, parentAndPosition:Option[RuleParentInfo], top: ScalaFormattingRule, matcher: ScalaFormattingRuleMatcher): Option[RuleMatch] = check(block.getSubBlocks().toList, parentAndPosition, top, matcher)

  /**
   * Check whether some continuous subsequence of the given sequence of blocks complies with the rule.
   * @param blocks
   * @return (before, found, after) where found is a list of blocks that comply with the rule and
   *         before ++ found ++ after == blocks
   */
  def checkSome(blocks: List[Block], parentAndPosition:Option[RuleParentInfo], top: ScalaFormattingRule, matcher: ScalaFormattingRuleMatcher): Option[(List[Block], RuleMatch, List[Block])]

  def getPresetIndentType: Option[IndentType]

  def getPriority: Int

  override def equals(other: Any) = {
    other match {
      case rule: ScalaFormattingRule =>rule.id == id
    }
  }

  override def toString = id

  /**
   * Binds this rule to given anchor. All rules bound to the same anchor should have the same resulting formatting settings.
   * @param anchor
   */
  def anchor(anchor: ScalaFormattingRule.Anchor): ScalaFormattingRule

  def anchor: Option[ScalaFormattingRule.Anchor]
}

object ScalaFormattingRule {

  type Anchor = String

  val RULE_PRIORITY_DEFAULT = 0
}