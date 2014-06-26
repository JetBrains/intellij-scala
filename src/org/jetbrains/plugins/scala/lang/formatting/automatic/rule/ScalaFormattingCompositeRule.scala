package org.jetbrains.plugins.scala
package lang.formatting.automatic.rule

import com.intellij.formatting.Block
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.IndentType
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.ScalaFormattingRule._
import scala.Some
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.relations.RuleRelation
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.matching.{RuleParentInfo, ScalaFormattingRuleMatcher}

/**
 * @author Roman.Shein
 *         Date: 09.09.13
 */
class ScalaFormattingCompositeRule private (val composingConditions: List[ScalaFormattingRule],
                                   val indentType: Option[IndentType.IndentType],
                                   val priority: Int,
                                   val id: String,
                                   val relations: Set[(RuleRelation, List[String])] = Set(),
                                   val tag: Option[String] = None
                                   ) extends ScalaFormattingRule {

//  def composingConditions = composingConditionsIds.map(getRule)

//  private def this(composingConditions: List[ScalaFormattingRule],
//  indentType: Option[IndentType.IndentType],
//  priority: Int,
//  id: String) = this(composingConditions, indentType, priority, id)

  override def checkSome(blocks: List[Block],
                         parentAndPosition: Option[RuleParentInfo],
                         top: ScalaFormattingRule,
                         matcher: ScalaFormattingRuleMatcher): Option[(List[Block], RuleMatch, List[Block])] = {
//    println("checking composite rule " + id)
    val ruleInstance = matcher.ruleInstance(parentAndPosition, this, top)
    blocks.foldLeft(List[Block](), None: Option[RuleMatch], blocks, true)(
      (acc, block) => {
        val (before, found, tail, proceed) = acc
        //attempt to check the conditions composing this rule starting from current block
        if (proceed) {
          val composingCheckFailed = (None: Option[List[RuleMatch]], tail, false)
          childrenWithPosition.foldLeft(None: Option[List[RuleMatch]], tail, true)((acc, compCondition) => {
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

  override def getPriority: Int = priority

  /**
   * Adds a tag to the rule so that ruleInstance created by this concrete rule can be distinguished when building dummy
   * rule instances. It is used for mapping between old and new formatting settings.
   * @param tag
   * @return
   */
  override def tag(tag: String): ScalaFormattingRule = registerRule(new ScalaFormattingCompositeRule(composingConditions, indentType, priority, id, relations, Some(tag)))

  override def childrenWithPosition: List[(ScalaFormattingRule, Int)] = composingConditions.zipWithIndex

  override protected def addRelation(relation: RuleRelation, args: List[String]): ScalaFormattingRule =
    new ScalaFormattingCompositeRule(composingConditions, indentType, priority, id, relations + ((relation, args)), tag)
}

object ScalaFormattingCompositeRule {
  def apply(id: String, conditions: ScalaFormattingRule*) = addRule(
    new ScalaFormattingCompositeRule(conditions.toList, None, ScalaFormattingRule.RULE_PRIORITY_DEFAULT, id)
  )
  def apply(priority: Int, id: String,  conditions: ScalaFormattingRule*) = addRule(
    new ScalaFormattingCompositeRule(conditions.toList, None, priority, id)
  )
}
