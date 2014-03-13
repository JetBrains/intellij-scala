package org.jetbrains.plugins.scala
package lang.formatting.automatic.rule

import com.intellij.formatting.Block
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.{ScalaFormattingRuleMatcher, RuleParentInfo, IndentType}
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.ScalaFormattingRule._
import scala.Some

/**
 * @author Roman.Shein
 *         Date: 09.09.13
 */
class ScalaFormattingCompositeRule private (val composingConditionsIds: List[String],
                                   val indentType: Option[IndentType.IndentType],
                                   val priority: Int,
                                   val id: String,
                                   val anchor: Option[Anchor] = None) extends ScalaFormattingRule {

  def composingConditions = composingConditionsIds.map(getRule)

  def this(composingConditions: List[ScalaFormattingRule],
  indentType: Option[IndentType.IndentType],
  priority: Int,
  id: String) = this(composingConditions.map(_.id), indentType, priority, id, None)

  override def checkSome(blocks: List[Block],
                         parentAndPosition: Option[RuleParentInfo],
                         top: ScalaFormattingRule,
                         matcher: ScalaFormattingRuleMatcher): Option[(List[Block], RuleMatch, List[Block])] = {
    println("checking composite rule " + id)
    val ruleInstance = matcher.ruleInstance(parentAndPosition, this, top)
    blocks.foldLeft(List[Block](), None: Option[RuleMatch], blocks, true)(
      (acc, block) => {
        val (before, found, tail, proceed) = acc
        //attempt to check the conditions composing this rule starting from current block
        if (proceed) {
          val composingCheckFailed = (None: Option[List[RuleMatch]], tail, false)
          composingConditions.zipWithIndex.foldLeft(None: Option[List[RuleMatch]], tail, true)((acc, compCondition) => {
            val (condition, position) = compCondition
            val parentInfo = RuleParentInfo(ruleInstance, position)
            val (found, tail, proceed) = acc
            if (!proceed) {
              (found, tail, false)
            } else {
              condition.checkSome(tail, Some(parentInfo), top, matcher) match {
                case None => composingCheckFailed
                case Some((innerBefore, innerFound, innerAfter)) =>
                  if (innerBefore.isEmpty) (Some(innerFound :: found.getOrElse(List[RuleMatch]())), innerAfter, true)
                  else composingCheckFailed
              }
            }
          }) match {
            case (None, _, true) => (block :: before, Some(ruleInstance.createMatch(found)), tail.tail, true)
            case (Some(innerFound), innerTail, _) => (before, Some(ruleInstance.createMatch(innerFound)), innerTail, false)
            case _ => (before, None, tail, false)
          }
        } else {
          (before, found, tail, false)
        }
      }) match {
      case (_, None, _, _) => None
      case (before, Some(found), after, _) => Some((before, found, after))
    }
  }

  override def getPresetIndentType: Option[IndentType.IndentType] = indentType

  /**
   * Returns clusters of rules that should be formatted with the same settings.
   * @return
   */
//  override def getSameFormattingRules(instances: List[ScalaFormattingRuleInstance]): List[Set[ScalaFormattingRuleInstance]] = {
//    (for (clusterIndices <- sameFormattingIndices) yield
//      instances.filter(instance => instance.parentAndPosition match {
//        case Some(parentInfo) => parentInfo.parent == this && clusterIndices.contains(parentInfo.position)
//        case _ => false
//      }).groupBy(_.root).values.flatten).map(_.toSet)
//    //sameFormattingIndices.map((indices: Set[Int]) => indices.map((index: Int) => ScalaFormattingRuleInstance(Some(RuleParentInfo(this, index)), rulesArray(index))).filter(instances.contains(_)))
//  }

  override def getPriority: Int = priority

  override def anchor(anchor: Anchor) = registerAnchor(new ScalaFormattingCompositeRule(composingConditionsIds, indentType, priority, id+"|-"+anchor, Some(anchor)))
}

object ScalaFormattingCompositeRule {
  def apply(id: String, conditions: ScalaFormattingRule*) = addRule(
    new ScalaFormattingCompositeRule(conditions.toList, None, ScalaFormattingRule.RULE_PRIORITY_DEFAULT, id)
  )
  def apply(priority: Int, id: String,  conditions: ScalaFormattingRule*) = addRule(
    new ScalaFormattingCompositeRule(conditions.toList, None, priority, id)
  )
}
