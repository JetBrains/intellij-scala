package org.jetbrains.plugins.scala
package lang.formatting.automatic.settings.matching

import com.intellij.formatting.WrapType
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.ScalaFormattingRule
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings._
import scala.Some

/**
 * @author Roman.Shein
 *         Date: 07.11.13
 */
class ScalaBlockFormatterEntry (val spacing: SpacingInfo,
                               val indentInfo: Option[IndentInfo],
                               val alignment: AlignmentSetting,
                               val wrap: WrapSetting,
                               val instances: List[ScalaBlock],
                               val rule: ScalaFormattingRuleInstance,
                               val originatingFromNoSpaceChild: Boolean = false) {
  def setIndent(option: Option[IndentInfo]) = new ScalaBlockFormatterEntry(spacing, option, alignment, wrap, instances, rule)

  def setWrap(wrapType: WrapType) = new ScalaBlockFormatterEntry(spacing, indentInfo, alignment, new WrapSetting(true, Some(wrapType)), instances, rule)

  def setSpacing(spacing: SpacingInfo) = new ScalaBlockFormatterEntry(spacing, indentInfo, alignment, wrap, instances,
  rule, originatingFromNoSpaceChild)

  def setAlignment(alignmentNeeded: Boolean) = new ScalaBlockFormatterEntry(spacing, indentInfo,
    AlignmentSetting(alignmentNeeded), wrap, instances, rule, originatingFromNoSpaceChild)

  override def toString = " | " + spacing.toString +
                          " | " + indentInfo.map(_.toString).getOrElse("Indent: Unknown") +
                          " | " + wrap.toString +
                          " | " + alignment.toString

  override def equals(other: Any) = other match {
      case entry: ScalaBlockFormatterEntry => entry.spacing == spacing &&
              entry.indentInfo == indentInfo &&
              entry.alignment == alignment &&
              entry.wrap == wrap &&
              entry.rule == rule
      case _ => false
    }

  override def hashCode = rule.hashCode

  def discardInstances = new ScalaBlockFormatterEntry(spacing, indentInfo, alignment, wrap, List[ScalaBlock](), rule) //TODO: get rid of this


  def reduceIndentTo(other: ScalaBlockFormatterEntry): Option[ScalaBlockFormatterEntry] = {
    (indentInfo, other.indentInfo) match {
      case (myIndent, otherIndent) if myIndent == otherIndent => Some(this)
      case (None, otherIndent) => Some(setIndent(otherIndent))
      case (Some(myIndentInfo), Some(otherIndentInfo)) =>
        if (myIndentInfo.indentLength != otherIndentInfo.indentLength ||
                myIndentInfo.indentRelativeToDirectParent != otherIndentInfo.indentRelativeToDirectParent) return None
        if (myIndentInfo.indentType == None) Some(setIndent(other.indentInfo)) else None
      case _ => None
    }
  }

  def reduceNewlineCount: ScalaBlockFormatterEntry = {
    val newSpacing = spacing.setLineFeeds(
      if (spacing.lineBreaksCount > 0) spacing.lineBreaksCount - 1 else 0
    )
    new ScalaBlockFormatterEntry(newSpacing, indentInfo, alignment, wrap, instances, rule, originatingFromNoSpaceChild)
  }

  def reduceWrapTo(other: ScalaBlockFormatterEntry): Option[ScalaBlockFormatterEntry] = {
    if (wrap == other.wrap) Some(this)
    else if (!wrap.wrapDefined && other.wrap.wrapDefined) {
      val wrapType = other.wrap.wrapType.get
      if (ScalaFormattingRuleMatcher.wrapTypeApplicable(instances ::: other.instances, wrapType)) {
        Some(reduceNewlineCount.setWrap(wrapType))
      } else None
    } else None
  }
}

object ScalaBlockFormatterEntry {
  def apply(spacing: SpacingInfo, indentInfo: IndentInfo, block: ScalaBlock,
            ruleInstance: ScalaFormattingRuleInstance, originatingFromNoSpaceChild: Boolean, wrap: Boolean): ScalaBlockFormatterEntry =
    new ScalaBlockFormatterEntry(spacing, Some(indentInfo), AlignmentSetting(false), WrapSetting(false), List[ScalaBlock](block), ruleInstance, originatingFromNoSpaceChild)
  def apply(spacing: SpacingInfo, indentInfo: Option[IndentInfo], block: ScalaBlock,
            ruleInstance: ScalaFormattingRuleInstance, originatingFromNoSpaceChild: Boolean, wrap: Boolean): ScalaBlockFormatterEntry =
    new ScalaBlockFormatterEntry(spacing, indentInfo, AlignmentSetting(false), WrapSetting(wrap), List[ScalaBlock](block), ruleInstance, originatingFromNoSpaceChild)
  def apply(spacing: SpacingInfo, indentInfo: IndentInfo, block: ScalaBlock,
            ruleInstance: ScalaFormattingRuleInstance, wrap: Boolean = false): ScalaBlockFormatterEntry =
    new ScalaBlockFormatterEntry(spacing, Some(indentInfo), AlignmentSetting(false), WrapSetting(wrap), List[ScalaBlock](block), ruleInstance)
  def apply(spacing: SpacingInfo, block: ScalaBlock, ruleInstance: ScalaFormattingRuleInstance): ScalaBlockFormatterEntry =
    new ScalaBlockFormatterEntry(spacing, None, AlignmentSetting(false), WrapSetting(false), List[ScalaBlock](block), ruleInstance)
  def apply(ruleInstance: ScalaFormattingRuleInstance): ScalaBlockFormatterEntry =
    new ScalaBlockFormatterEntry(null, None, null, null, List(), ruleInstance)
}
