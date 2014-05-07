package org.jetbrains.plugins.scala
package lang.formatting.automatic.settings

import com.intellij.formatting.{WrapType, Wrap, Alignment}
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.{ScalaFormattingRule, ScalaBlockRule}
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock

/**
 * @author Roman.Shein
 *         Date: 07.11.13
 */
class ScalaBlockFormatterEntry(val spacing: SpacingInfo,
                               val indentInfo: Option[IndentInfo],
                               val alignment: AlignmentSetting,
                               val wrap: WrapSetting,
                               val instances: List[ScalaBlock],
                               val ruleInstance: ScalaFormattingRuleInstance,
                               val originatingFromNoSpaceChild: Boolean = false) {
  def setWrap(wrapType: WrapType) = new ScalaBlockFormatterEntry(spacing, indentInfo, alignment, new WrapSetting(wrap.needWrap, Some(wrapType)), instances, ruleInstance)

  def setSpacing(spacing: SpacingInfo) = new ScalaBlockFormatterEntry(spacing, indentInfo, alignment, wrap, instances,
  ruleInstance, originatingFromNoSpaceChild)

  def setAlignment(alignmentNeeded: Boolean) = new ScalaBlockFormatterEntry(spacing, indentInfo,
    AlignmentSetting(alignmentNeeded), wrap, instances, ruleInstance, originatingFromNoSpaceChild)

  override def toString = " | " + spacing.toString +
                          " | " + indentInfo.map(_.toString).getOrElse("Indent: Unknown") +
                          " | " + wrap.toString +
                          " | " + alignment.toString

  override def equals(other: Any) = other match {
      case entry: ScalaBlockFormatterEntry => entry.spacing == spacing &&
              entry.indentInfo == indentInfo &&
              entry.alignment == alignment &&
              entry.wrap == wrap &&
              entry.ruleInstance == ruleInstance
      case _ => false
    }

  override def hashCode = ruleInstance.hashCode

  def discardInstances = new ScalaBlockFormatterEntry(spacing, indentInfo, alignment, wrap, List[ScalaBlock](), ruleInstance) //TODO: get rid of this

}

object ScalaBlockFormatterEntry {
  def apply(spacing: SpacingInfo, indentInfo: IndentInfo, block: ScalaBlock,
            ruleInstance: ScalaFormattingRuleInstance, originatingFromNoSpaceChild: Boolean): ScalaBlockFormatterEntry =
    new ScalaBlockFormatterEntry(spacing, Some(indentInfo), AlignmentSetting(false), WrapSetting(false), List[ScalaBlock](block), ruleInstance, originatingFromNoSpaceChild)
  def apply(spacing: SpacingInfo, indentInfo: IndentInfo, block: ScalaBlock,
            ruleInstance: ScalaFormattingRuleInstance): ScalaBlockFormatterEntry =
    new ScalaBlockFormatterEntry(spacing, Some(indentInfo), AlignmentSetting(false), WrapSetting(false), List[ScalaBlock](block), ruleInstance)
  def apply(spacing: SpacingInfo, block: ScalaBlock, ruleInstance: ScalaFormattingRuleInstance): ScalaBlockFormatterEntry =
    new ScalaBlockFormatterEntry(spacing, None, AlignmentSetting(false), WrapSetting(false), List[ScalaBlock](block), ruleInstance)
  def apply(ruleInstance: ScalaFormattingRuleInstance): ScalaBlockFormatterEntry =
    new ScalaBlockFormatterEntry(null, None, null, null, List(), ruleInstance)
}
