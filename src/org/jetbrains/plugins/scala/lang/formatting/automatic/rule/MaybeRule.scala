package org.jetbrains.plugins.scala
package lang.formatting.automatic.rule

import com.intellij.formatting.Block
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.{ScalaFormattingRuleMatcher, RuleParentInfo, IndentType}
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.ScalaFormattingRule._
import scala.Some

/**
 * @author Roman.Shein
 *         Date: 12.09.13
 */
class MaybeRule(val inner: ScalaFormattingRule,
                val indentType: Option[IndentType.IndentType],
                val priority: Int,
                val id: String,
                val anchor: Option[Anchor] = None) extends ScalaFormattingRule{

  override def checkSome(blocks: List[Block],
                         parentAndPosition: Option[RuleParentInfo],
                         top: ScalaFormattingRule,
                         matcher: ScalaFormattingRuleMatcher): Option[(List[Block], RuleMatch, List[Block])] = {
    println("checking maybe rule " + id)
    val ruleInstance = matcher.ruleInstance(parentAndPosition, this, top)
    inner.checkSome(blocks, Some(RuleParentInfo(ruleInstance, 0)), top, matcher) match {
      case None                         => Some((List.empty, ruleInstance.createMatch(), blocks))
      case Some((before, found, after)) => Some((before, ruleInstance.createMatch(found), after))
    }
  }

  /**
   * Returns clusters of rules that should be formatted with the same settings.
   * @return
   */
//  override def getSameFormattingRules(instances: List[ScalaFormattingRuleInstance]): List[Set[ScalaFormattingRuleInstance]] =
//    //List[Set[ScalaFormattingRuleInstance]]()
//    inner.getSameFormattingRules(instances)

  override def getPresetIndentType: Option[IndentType.IndentType] = indentType

  override def getPriority: Int = priority

  override def anchor(anchor: Anchor) = new MaybeRule(inner, indentType, priority, id, Some(anchor))

}

object MaybeRule {
  //def apply() = new MaybeRule()
  def apply(inner: ScalaFormattingRule, id: String) = new MaybeRule(inner, None, inner.getPriority, id)
  def apply(inner: ScalaFormattingRule, indentType: IndentType.IndentType, id: String) = new MaybeRule(inner, Some(indentType), inner.getPriority, id)
}
