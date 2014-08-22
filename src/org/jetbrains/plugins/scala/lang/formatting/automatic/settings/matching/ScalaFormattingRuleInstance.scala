package org.jetbrains.plugins.scala
package lang.formatting.automatic.settings.matching

import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.FormattingSettings
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.ScalaFormattingRule
import com.intellij.openapi.util.TextRange

/**
 * @author Roman.Shein
 *         Date: 19.11.13
 */
class ScalaFormattingRuleInstance(val parentAndPosition: Option[RuleParentInfo], val rule: ScalaFormattingRule, val root: ScalaFormattingRule) {

  def position = parentAndPosition match {
    case Some(value) => Some(value.position)
    case None => None
  }

  def parent = parentAndPosition match {
    case Some(value) => Some(value.parent)
    case None => None
  }

  override def hashCode = parentAndPosition.hashCode * root.hashCode + rule.hashCode

  override def equals(other: Any) = {
    other match {
      case instance: ScalaFormattingRuleInstance =>
        parentAndPosition == instance.parentAndPosition && rule == instance.rule && root == instance.root
      case _ => false
    }
  }

  def getTreeString: String = {
    (parentAndPosition match {
      case Some(parentInfo) => parentInfo.parent.getTreeString + "\nposition: " + parentInfo.position + "; "
      case _ => ""
    }) + rule.toString
  }

  override def toString = rule.toString + (parentAndPosition match {
    case Some(parentInfo) => "; position: " + parentInfo.position
    case None => ""
  })

  class MatchContext private[ScalaFormattingRuleInstance] (val matchedBlocks: List[ScalaBlock],
                                                           val parentAndIndexInfo: Option[(ScalaBlock, Int)],
                                                           val formatBlock: Option[ScalaBlock]){
  }

  object MatchContext {
    def apply(matchedBlocks: List[ScalaBlock], formatBlock: ScalaBlock) = new MatchContext(matchedBlocks, None, Some(formatBlock))
    def apply(matchedBlocks: List[ScalaBlock], parent: ScalaBlock, index: Int) = new MatchContext(matchedBlocks, Some((parent, index)), None)
    def apply(childMatches: List[ScalaFormattingRuleInstance#RuleMatch]) = new MatchContext(
      childMatches.map(_.matchContex.matchedBlocks).flatten, None, None
    )
  }

  class RuleMatch private[ScalaFormattingRuleInstance] (val childMatches: List[ScalaFormattingRuleInstance#RuleMatch],
                                                        val matchContex: MatchContext) {

//    override def equals(other: Any): Boolean = {
//      other match {
//        case otherMatch: RuleMatch =>
//
//        case _ => false
//      }
//    }

    def formatBlock = matchContex.formatBlock

    override def hashCode: Int = rule.hashCode

    val rule = ScalaFormattingRuleInstance.this

    def getFormattingDefiningWhitespace: Option[String] = getFormattingDefiningBlock.map(_.getInitialWhiteSpace)

    def getFormattingWhitespaceTextRange: Option[TextRange] = getFormattingDefiningBlock.map(_.getInitialSpacing.map(_.getTextRange)).flatten

    //TODO: make this private so ScalaBlock does not make any appearences in formatter engine
    def getFormattingDefiningBlock: Option[ScalaBlock] =
      formatBlock match {
        case Some(valBlock) => Some(valBlock)
        case _ => childMatches.foldRight(None: Option[ScalaBlock])((childMatch, acc) => //fold right since children are in reversed order
          acc.map(Some(_)).getOrElse(childMatch.getFormattingDefiningBlock)) //TODO: check if this Some is idea bug
      }

    def getSubRules: List[ScalaFormattingRuleInstance#RuleMatch] = childMatches//.map(_.getSubRules).flatten

    private def toStringHelper(offset: Int, settings:Option[FormattingSettings]): String = {
      val res = new StringBuilder()
      for (i <- 1 to offset) res.append(" ")
      res.append("|" + rule.toString)
      settings match {
        case Some(_settings) => _settings.getEntryForRule(rule).flatten match {
          case Some(entry) => res.append(entry.toString)
          case _ => res.append(" no settings derived for this match")
        }
        case None =>
      }

      for (child <- childMatches.reverse) res.append("\n" + child.toStringHelper(offset + 2, settings)) // children are appended to the end of list, so reverse is needed
      res.toString()
    }
    override def toString = toStringHelper(0, None)

    def toStringWithSettings(settings: FormattingSettings) = toStringHelper(0, Some(settings))
  }

  def createMatch(childMatches: List[ScalaFormattingRuleInstance#RuleMatch]): ScalaFormattingRuleInstance#RuleMatch = {
    val res = new RuleMatch(childMatches.toList, MatchContext(childMatches))
    res
  }
  def createMatch(childMatches: ScalaFormattingRuleInstance#RuleMatch*): ScalaFormattingRuleInstance#RuleMatch = createMatch(childMatches.toList)
  def createMatch(childMatch: Option[ScalaFormattingRuleInstance#RuleMatch]): ScalaFormattingRuleInstance#RuleMatch = childMatch match {
    case Some(child) => createMatch(child)
    case None => createMatch()
  }
  def createMatch(block: ScalaBlock, childMatches: ScalaFormattingRuleInstance#RuleMatch*): ScalaFormattingRuleInstance#RuleMatch = {
    val res = new RuleMatch(childMatches.toList, MatchContext(List(block), block))
    res
  }
}
