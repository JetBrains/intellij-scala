package org.jetbrains.plugins.scala
package lang.formatting.automatic.rule

import com.intellij.formatting._
import scala.collection.immutable.Seq
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import scala.Some
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.{ScalaFormattingRuleMatcher, RuleParentInfo, IndentType}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.ScalaFormattingRule._

/**
 * @author Roman.Shein
 *         Date: 09.09.13
 */
class ScalaBlockRule(val testFunction: Block => Boolean,
                     val indentType: Option[IndentType.IndentType],
                     val id: String,
                     val anchor: Option[Anchor] = None
                    ) extends ScalaFormattingRule {

  override def checkSome(blocks: List[Block],
                         parentInfo: Option[RuleParentInfo],
                         top: ScalaFormattingRule,
                         matcher: ScalaFormattingRuleMatcher) = {
    println("checking block rule " + id)
    val (before, found, after) = blocks.foldLeft(List[Block](), None: Option[Block], List[Block]())((acc, block) => {
      val (before, found, after) = acc
      if (found.isDefined) (before, found, block::after)
      else if (testFunction(block)) (before, Some(block), after)
      else (block::before, found, after)
    })
    val ruleInstance = matcher.ruleInstance(parentInfo, this, top)
    if (found.isDefined && found.get.isInstanceOf[ScalaBlock]) Some(before.reverse, ruleInstance.createMatch(found.get.asInstanceOf[ScalaBlock]), after.reverse) else None
  }

  /**
   * Returns clusters of rules that should be formatted with the same settings.
   * @return
   */
//  override def getSameFormattingRules(instances: List[ScalaFormattingRuleInstance]): List[Set[ScalaFormattingRuleInstance]] = List[Set[ScalaFormattingRuleInstance]]()

  override def getPresetIndentType: Option[IndentType.IndentType] = indentType

  override def getPriority: Int = ScalaFormattingRule.RULE_PRIORITY_DEFAULT

  override def anchor(anchor: Anchor) = new ScalaBlockRule(testFunction, indentType, id, Some(anchor))

}

object ScalaBlockRule {
  def apply(testFunction: Block => Boolean, id: String) = new ScalaBlockRule(testFunction, None, id)
  def apply(expectedText: String, id:String) = new ScalaBlockRule(
  {
    case scalaBlock: ScalaBlock => scalaBlock.getNode.getText == expectedText
    case _ => false
  }, None, id
  )
  def apply(id:String, expectedType: IElementType*) = new ScalaBlockRule(
  {
    case scalaBlock: ScalaBlock => expectedType.contains(scalaBlock.getNode.getElementType)
    case _ => false
  }, None, id
  )
  def apply(id:String, expectedType: List[IElementType]) = new ScalaBlockRule(
  {
    case scalaBlock: ScalaBlock => expectedType.contains(scalaBlock.getNode.getElementType)
    case _ => false
  }, None, id
  )
  def apply(expectedType: IElementType, expectedText: String, id: String) = new ScalaBlockRule(
  {
    case scalaBlock: ScalaBlock => scalaBlock.getNode.getElementType == expectedType && scalaBlock.getNode.getText == expectedText
    case _ => false
  }, None, id
  )

}


