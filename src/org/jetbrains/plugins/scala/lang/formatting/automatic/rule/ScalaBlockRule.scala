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
class ScalaBlockRule private (val testFunction: Block => Boolean,
                     val indentType: Option[IndentType.IndentType],
                     val id: String,
                     val structId: String,
                     val anchor: Option[Anchor] = None,
                     val tag: Option[String] = None,
                     val alignmentAnchor: Option[String] = None
                    ) extends ScalaFormattingRule {

  private def this(testFunction: Block => Boolean, indentType: Option[IndentType.IndentType], id: String) =
    this(testFunction, indentType, id, id)

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

  override def anchor(anchor: Anchor) = {
    assert(this.anchor.isEmpty)
    assert(alignmentAnchor.isEmpty)
    registerRule(new ScalaBlockRule(testFunction, indentType, id+"|-"+anchor, structId, Some(anchor), None, Some(anchor)))
  }

  /**
   * Adds a tag to the rule so that ruleInstance created by this concrete rule can be distinguished when building dummy
   * rule instances. It is used for mapping between old and new formatting settings.
   * @param tag
   * @return
   */
  override def tag(tag: String): ScalaFormattingRule = registerRule(new ScalaBlockRule(testFunction, indentType, id+"*"+tag, structId, None, Some(tag)))

  override def childrenWithPosition: List[(ScalaFormattingRule, Int)] = List()

  override def alignmentAnchor(alignmentAnchor: String) = {
    assert(anchor.isEmpty)
    assert(this.alignmentAnchor.isEmpty)
    registerRule(new ScalaBlockRule(testFunction, indentType, id+"&"+alignmentAnchor, structId, None, None, Some(alignmentAnchor)))
  }

}

object ScalaBlockRule {
  def apply(testFunction: Block => Boolean, id: String) = addRule(new ScalaBlockRule(testFunction, None, id))
  def apply(expectedText: String, id:String) = addRule(new ScalaBlockRule(
  {
    case scalaBlock: ScalaBlock => scalaBlock.getNode.getText == expectedText
    case _ => false
  }, None, id
  ))
  def apply(id:String, expectedType: IElementType*) = addRule(new ScalaBlockRule(
  {
    case scalaBlock: ScalaBlock => expectedType.contains(scalaBlock.getNode.getElementType)
    case _ => false
  }, None, id
  ))
  def apply(id:String, expectedType: List[IElementType]) = addRule(new ScalaBlockRule(
  {
    case scalaBlock: ScalaBlock => expectedType.contains(scalaBlock.getNode.getElementType)
    case _ => false
  }, None, id
  ))
  def apply(expectedType: IElementType, expectedText: String, id: String) = addRule(new ScalaBlockRule(
  {
    case scalaBlock: ScalaBlock => scalaBlock.getNode.getElementType == expectedType && scalaBlock.getNode.getText == expectedText
    case _ => false
  }, None, id
  ))
}


