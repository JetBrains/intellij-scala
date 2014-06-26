package org.jetbrains.plugins.scala
package lang.formatting.automatic.settings.matching

trait SettingDeductionFailLogger {

  def logEntryUnificationFail(entry1: ScalaBlockFormatterEntry, entry2: ScalaBlockFormatterEntry, comment: String)
}
