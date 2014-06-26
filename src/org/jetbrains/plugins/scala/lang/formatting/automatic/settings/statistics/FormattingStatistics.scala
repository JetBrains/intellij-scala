package org.jetbrains.plugins.scala
package lang.formatting.automatic.settings.statistics

import scala.collection.mutable
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.matching.{ScalaBlockFormatterEntry, SettingDeductionFailLogger, ScalaFormattingRuleInstance}

class FormattingStatistics extends SettingDeductionFailLogger {

  private val initialSettings: mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]] = mutable.Map()

  private val relationSettings: mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]] = mutable.Map()

  def addInitialSettings(settings: mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]]) = {
    for ((ruleInstance, ruleEntries) <- settings) {
      initialSettings.put(ruleInstance, ruleEntries ::: initialSettings.getOrElse(ruleInstance, List()))
    }
  }

  def getInitialSettings = initialSettings

  def addRelationSettings(settings: mutable.Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]]) = {

  }

  val notUnifiedEntries =
    mutable.Map[ScalaFormattingRuleInstance, List[(ScalaBlockFormatterEntry, ScalaBlockFormatterEntry, String)]]()

  override def logEntryUnificationFail(entry1: ScalaBlockFormatterEntry, entry2: ScalaBlockFormatterEntry, comment: String): Unit = {
    assert(entry1.rule == entry2.rule)
    val ruleInstance = entry1.rule
    notUnifiedEntries.put(ruleInstance, (entry1, entry2, comment) :: notUnifiedEntries.getOrElse(ruleInstance, List()))
  }
}
