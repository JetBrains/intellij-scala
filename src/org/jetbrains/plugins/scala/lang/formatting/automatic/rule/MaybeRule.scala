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
class MaybeRule private (val innerId: String,
                val indentType: Option[IndentType.IndentType],
                val priority: Int,
                val id: String,
                val structId: String,
                val anchor: Option[Anchor] = None,
                val tag: Option[String] = None,
                val alignmentAnchor: Option[Anchor] = None) extends ScalaFormattingRule{

  private def this(innerRule: ScalaFormattingRule,
      indentType: Option[IndentType.IndentType],
      priority: Int,
      id: String) = this(innerRule.id, indentType, priority, id, id)

  def inner = getRule(innerId)

  override def checkSome(blocks: List[Block],
                         parentAndPosition: Option[RuleParentInfo],
                         top: ScalaFormattingRule,
                         matcher: ScalaFormattingRuleMatcher): Option[(List[Block], RuleMatch, List[Block])] = {
    println("checking maybe rule " + id)
    val ruleInstance = matcher.ruleInstance(parentAndPosition, this, top)
    inner.checkSome(blocks, Some(RuleParentInfo(ruleInstance, 0)), top, matcher) match {
      case Some((before, found, after)) if before.isEmpty => Some((before, ruleInstance.createMatch(found), after))
      case _ => Some((List.empty, ruleInstance.createMatch(), blocks))
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

  override def anchor(anchor: Anchor) = {
    assert(this.anchor.isEmpty)
    assert(alignmentAnchor.isEmpty)
    registerRule(new MaybeRule(innerId, indentType, priority, id + "|-" + anchor, structId, Some(anchor), None, Some(anchor)))
  }

  /**
   * Adds a tag to the rule so that ruleInstance created by this concrete rule can be distinguished when building dummy
   * rule instances. It is used for mapping between old and new formatting settings.
   * @param tag
   * @return
   */
  override def tag(tag: String): ScalaFormattingRule = registerRule(new MaybeRule(innerId, indentType, priority, id + "*" + tag, structId, None, Some(tag)))

  override def childrenWithPosition: List[(ScalaFormattingRule, Int)] = {List((inner, 0))}

  override def alignmentAnchor(alignmentAnchor: String): ScalaFormattingRule = {
    assert(anchor.isEmpty)
    assert(this.alignmentAnchor.isEmpty)
    registerRule(new MaybeRule(innerId, indentType, priority, id + "$" + alignmentAnchor, structId, None, None, Some(alignmentAnchor)))
  }
}

object MaybeRule {
  //def apply() = new MaybeRule()
  def apply(inner: ScalaFormattingRule, id: String) = addRule(new MaybeRule(inner, None, inner.getPriority, id))
  def apply(inner: ScalaFormattingRule, indentType: IndentType.IndentType, id: String) = addRule(new MaybeRule(inner, Some(indentType), inner.getPriority, id))
}
