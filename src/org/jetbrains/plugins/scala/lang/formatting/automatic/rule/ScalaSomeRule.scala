package org.jetbrains.plugins.scala
package lang.formatting.automatic.rule

import com.intellij.formatting.Block
import scala.collection.immutable.Seq
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.{ScalaFormattingRuleMatcher, RuleParentInfo, IndentType}
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.IndentType.IndentType
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.ScalaFormattingRule._
import scala.Some

/**
 * @author Roman.Shein
 *         Date: 09.09.13
 */
class ScalaSomeRule private (val minimalCount: Int,
                    val innerConditionId: String,
                    val indentType: Option[IndentType],
                    val priority: Int,
                    val id: String,
                    val structId: String,
                    val anchor: Option[Anchor] = None,
                    val tag: Option[String] = None,
                    val alignmentAnchor: Option[String] = None
                    )
        extends ScalaFormattingRule {

  def innerCondition = getRule(innerConditionId)

  def this(minimalCount: Int,
  innerCondition: ScalaFormattingRule,
  indentType: Option[IndentType],
  priority: Int,
  id: String) = this(minimalCount, innerCondition.id, indentType, priority, id, id)

  override def checkSome(blocks: List[Block],
                         parentInfo: Option[RuleParentInfo],
                         top: ScalaFormattingRule,
                         matcher: ScalaFormattingRuleMatcher) = {
    println("checking some rule " + id)
    val ruleInstance = matcher.ruleInstance(parentInfo, this, top)
    val (before, found, after, count, _) = blocks.foldLeft(List[Block](), None: Option[List[RuleMatch]], blocks, 0, true)(
      (acc, block) => {
      val (before, found, tail, count, proceed) = acc
      if (proceed && !tail.isEmpty) {
        (innerCondition.checkSome(tail, Some(RuleParentInfo(ruleInstance, 0)), top, matcher), found) match {
          case (None, None) => (block::before, found, tail.tail, count, true)
          case (None, Some(foundVal)) => (before, found, tail, count, false)
          case (Some((innerBefore, innerFound, innerAfter)), None) =>
            (innerBefore ++ before, Some(List[RuleMatch](innerFound)), innerAfter, 1, true)
          case (Some((innerBefore, innerFound, innerAfter)), Some(foundVal)) =>
            (innerBefore ++ before, Some(innerFound :: foundVal), innerAfter, count + 1, true)
        }
      } else (before, found, tail, count, proceed)
    })
    if (found.isDefined && count >= minimalCount) Some(before.reverse, ruleInstance.createMatch(found.get), after) else None
  }

  /**
   * Returns clusters of rules that should be formatted with the same settings.
   * @return
   */
//  override def getSameFormattingRules(instances: List[ScalaFormattingRuleInstance]): List[Set[ScalaFormattingRuleInstance]] = {
//      Set[ScalaFormattingRuleInstance](
//        instances.filter((instance: ScalaFormattingRuleInstance) =>
//          instance.parentAndPosition match {
//            case Some(parentAndPosition) => parentAndPosition.parent == this
//            case None => false
//          }): _*
//      ).groupBy(_.root).values.toList
//  }

  override def getPresetIndentType: Option[IndentType] = indentType

  override def getPriority: Int = priority

  override def anchor(anchor: Anchor) = {
    assert(this.anchor.isEmpty)
    assert(alignmentAnchor.isEmpty)
    registerRule(new ScalaSomeRule(minimalCount, innerConditionId, indentType, priority, id+"|-"+anchor, structId, Some(anchor)))
  }

  /**
   * Adds a tag to the rule so that ruleInstance created by this concrete rule can be distinguished when building dummy
   * rule instances. It is used for mapping between old and new formatting settings.
   * @param tag
   * @return
   */
  override def tag(tag: String): ScalaFormattingRule = registerRule(new ScalaSomeRule(minimalCount, innerConditionId, indentType, priority, id + "*" + tag, structId, None, Some(tag)))

  override def childrenWithPosition: List[(ScalaFormattingRule, Int)] =  List((innerCondition, 0))

  override def alignmentAnchor(alignmentAnchor: String): ScalaFormattingRule = {
    assert(anchor.isEmpty)
    assert(this.alignmentAnchor.isEmpty)
    registerRule(new ScalaSomeRule(minimalCount, innerConditionId, indentType, priority, id + "&" + alignmentAnchor, structId, None, None, Some(alignmentAnchor)))
  }
}

object ScalaSomeRule {
  def apply(minimalCount: Int, innerCondition: ScalaFormattingRule, id: String) = addRule(
    new ScalaSomeRule(minimalCount, innerCondition, None, ScalaFormattingRule.RULE_PRIORITY_DEFAULT, id)
  )
}
