package org.jetbrains.plugins.scala
package lang.formatting.automatic.rule

import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.{ScalaFormattingRuleMatcher, RuleParentInfo, IndentType}
import com.intellij.formatting.Block
import scala.collection.JavaConversions._
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import com.intellij.psi.tree.IElementType
import scala.annotation.tailrec
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.ScalaFormattingRule._
import scala.Some

/**
 * @author Roman.Shein
 *         Date: 27.11.13
 */
class ScalaBlockCompositeRule(val testFunction: Block => Boolean,
                              val compositeRule: ScalaFormattingRule,
                              val priority: Int,
                              val id: String,
                              val anchor: Option[Anchor] = None) extends ScalaFormattingRule {
  //this is  a transparent wrapper, only the underlying composite rule will be visible
  override def check(block: ScalaBlock,
          parentInfo: Option[RuleParentInfo],
          top: ScalaFormattingRule,
          matcher: ScalaFormattingRuleMatcher): Option[RuleMatch] = {
    val ruleInstance = matcher.ruleInstance(parentInfo, this, top)
      if (testFunction(block)) compositeRule.check(block, Some(RuleParentInfo(ruleInstance, 0)), top, matcher) match {
      case Some(found) =>
        Some(ruleInstance.createMatch(block, found))
      case None => None
    } else None
  }

  override def checkSome(blocks: List[Block],
                         parentInfo: Option[RuleParentInfo],
                         top: ScalaFormattingRule,
                         matcher: ScalaFormattingRuleMatcher): Option[(List[Block], RuleMatch, List[Block])] = {
    println("checking block composite rule " + id)
    val ruleInstance = matcher.ruleInstance(parentInfo, this, top)
    val newParentInfo = RuleParentInfo(ruleInstance, 0)
    @tailrec
    def findFirst(before: List[Block], after: List[Block]): Option[(List[Block], RuleMatch, List[Block])] = {
      after match {
        case head :: tail =>
          if (testFunction(head) && head.isInstanceOf[ScalaBlock]) {
            compositeRule.check(head.asInstanceOf[ScalaBlock], Some(newParentInfo), top, matcher) match {
              case Some(ruleMatch) => Some((before.reverse, ruleInstance.createMatch(head.asInstanceOf[ScalaBlock], ruleMatch), tail))
              case None => findFirst(head :: before, tail)
            }
          } else {
            findFirst(head :: before, tail)
          }
        case _ => None
      }
    }
    findFirst(List[Block](), blocks)
  }

  //  override def checkSome(blocks: List[Block]): Option[(List[Block], RuleMatch, List[Block])] = {
  //    println("checking block composite rule " + id)
  //    if (blocks.isEmpty) return None
  //    if (testFunction(blocks.head)) {
  //      val compositeRes = compositeRule.checkSome(blocks.head.getSubBlocks.toList)
  //      compositeRes match {
  //        case Some((before, found, after)) => Some(before, RuleMatch(this, found), after ++ blocks.tail)
  //        case None => None
  //      }
  //    } else None
  //  }

//  override def getSameFormattingRules(instances: List[ScalaFormattingRuleInstance]): List[Set[ScalaFormattingRuleInstance]] = compositeRule.getSameFormattingRules(
//    instances)

  override def getPresetIndentType: Option[IndentType.IndentType] = compositeRule.getPresetIndentType

  override def getPriority: Int = priority

  override def anchor(anchor: Anchor) = new ScalaBlockCompositeRule(testFunction, compositeRule, priority, id, Some(anchor))

}

object ScalaBlockCompositeRule {
  def apply(expectedText: String, compositeRule: ScalaFormattingRule, priority: Int, id: String) = new ScalaBlockCompositeRule(
  {
    case scalaBlock: ScalaBlock => scalaBlock.getNode.getText == expectedText
    case _ => false
  }, compositeRule, priority, id
  )

  def apply(expectedType: IElementType, compositeRule: ScalaFormattingRule, priority: Int, id: String) = new ScalaBlockCompositeRule(
  {
    case scalaBlock: ScalaBlock => scalaBlock.getNode.getElementType == expectedType
    case _ => false
  }, compositeRule, priority, id
  )

  def apply(compositeRule: ScalaFormattingRule, priority: Int, id: String, expectedTypes: List[IElementType]) = new ScalaBlockCompositeRule(
  {
    case scalaBlock: ScalaBlock => expectedTypes.contains(scalaBlock.getNode.getElementType)
    case _ => false
  }, compositeRule, priority, id
  )
}