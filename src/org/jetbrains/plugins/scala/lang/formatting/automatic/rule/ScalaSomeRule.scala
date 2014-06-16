package org.jetbrains.plugins.scala
package lang.formatting.automatic.rule

import com.intellij.formatting.Block
import scala.collection.immutable.Seq
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.{ScalaFormattingRuleMatcher, RuleParentInfo, IndentType}
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.IndentType.IndentType
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.ScalaFormattingRule._
import scala.Some
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.relations.RuleRelation

/**
 * @author Roman.Shein
 *         Date: 09.09.13
 */
class ScalaSomeRule private (val minimalCount: Int,
                    val innerCondition: ScalaFormattingRule,
                    val indentType: Option[IndentType],
                    val priority: Int,
                    val id: String,
                    val relations: Set[(RuleRelation, List[String])] = Set(),
                    val tag: Option[String] = None
                    )
        extends ScalaFormattingRule {

  override def checkSome(blocks: List[Block],
                         parentInfo: Option[RuleParentInfo],
                         top: ScalaFormattingRule,
                         matcher: ScalaFormattingRuleMatcher) = {
//    println("checking some rule " + id)
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

  override def getPresetIndentType: Option[IndentType] = indentType

  override def getPriority: Int = priority

  /**
   * Adds a tag to the rule so that ruleInstance created by this concrete rule can be distinguished when building dummy
   * rule instances. It is used for mapping between old and new formatting settings.
   * @param tag
   * @return
   */
  override def tag(tag: String): ScalaFormattingRule = registerRule(new ScalaSomeRule(minimalCount, innerCondition, indentType, priority, id, relations, Some(tag)))

  override def childrenWithPosition: List[(ScalaFormattingRule, Int)] =  List((innerCondition, 0))

  override protected def addRelation(relation: RuleRelation, args: List[String]): ScalaFormattingRule =
    new ScalaSomeRule(minimalCount, innerCondition, indentType, priority, id, relations + ((relation, args)), tag)
}

object ScalaSomeRule {
  def apply(minimalCount: Int, innerCondition: ScalaFormattingRule, id: String) = addRule(
    new ScalaSomeRule(minimalCount, innerCondition, None, ScalaFormattingRule.RULE_PRIORITY_DEFAULT, id)
  )
}
