package org.jetbrains.plugins.scala
package lang.formatting.automatic.settings

import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.IndentType.IndentType
import scala.collection.mutable
import com.intellij.openapi.util.TextRange

/**
 * @author Roman.Shein
 *         Date: 14.11.13
 */
class FormattingSettings(val normalIndentSize: Option[Int],
                         val continuationIndentSize: Option[Int],
                         val instances: Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]],
                         val whitespaceRangeToRuleInstance: Map[TextRange, ScalaFormattingRuleInstance]       ) {

  //TODO: remove all data on ScalaBlocks from final settings in a proper way (not relying on ScalaBLockFormatterEntry.discardInstances trick)

  def coversRule(rule: ScalaFormattingRuleInstance) = {
    instances.get(rule) match {
      case Some(entries) => !entries.isEmpty
      case None => false
    }
  }

  def getEntryForRule(rule: ScalaFormattingRuleInstance) = {
    instances.get(rule) match {
      case Some(entries) if !entries.isEmpty => Some(entries.head) //TODO: maybe add some more complicated conflict resolution
      case _ => None
    }
  }

  def rulesCovered = {instances.count((arg: (ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry])) => {
    val (rule, entries) = arg
    entries != null && entries.size > 0
  })}

  def addInstance(ruleInstance: ScalaFormattingRuleInstance, formattingEntry: ScalaBlockFormatterEntry) = new FormattingSettings(normalIndentSize, continuationIndentSize, instances + Pair(ruleInstance, formattingEntry.discardInstances :: instances.get(ruleInstance).getOrElse(List[ScalaBlockFormatterEntry]())), whitespaceRangeToRuleInstance)

  def unify(other: FormattingSettings, matcher: ScalaFormattingRuleMatcher): Option[FormattingSettings] = {
    def unifySize(size1: Option[Int], size2: Option[Int]): (Option[Int], Boolean) = {
      (size1, size2) match {
        case (None, None) => (None, true)
        case (Some(value), None) => (Some(value), true)
        case (None, Some(value)) => (Some(value), true)
        case (Some(value1), Some(value2)) if value1 == value2 => (Some(value1), true)
        case _ => (None, false)
      }
    }
    val (newNormalSize, normalOk) = unifySize(normalIndentSize, other.normalIndentSize)

    val (newContinuationSize, continuationOk) = unifySize(continuationIndentSize, other.continuationIndentSize)

    if (normalOk && continuationOk) {
      var newInstances = Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]]()
      for (entry <- instances) {
        newInstances += entry
      }
//
//      var settingsStack = List[FormattingSettings](this)
//      //      val conflicts = this.whitespaceRangeToRuleInstance.keys.toSet.intersect(other.whitespaceRangeToRuleInstance.keys.toSet).toList
//      val conflicts = whitespaceRangeToRuleInstance.filter({arg =>
//        val (range, rule) = arg
//        other.whitespaceRangeToRuleInstance.get(range) != rule
//      })
//
//
//      //{
//      //      ((spacingRange, rules)) => {other.whitespaceRangeToRuleInstance.get(spacingRange) match {
//      //        case Some(otherRules) => true
//      //        case None => true
//      //      }}})
//
//
//      for ((rule, otherEntries) <- other.instances) {
//        if (newInstances.contains(rule)) {
//          //this rule was present in the original settings object, conflict is possible
//          var splitEntries = List[ScalaBlockFormatterEntry]
//          for (otherEntry <- otherEntries) {
//            if (otherEntry.instances.map(_.getTextRange).intersect(conflicts))
//
//          }
//        } else {
//          //this rule was not present in the original settings object, just add it to common part for all
//          newInstances += ((rule, otherEntries))
//        }
//      }
//
//      def resolveConfilcts(acc: List[FormattingSettings], rulesToEntries: Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]]) = {
//        rulesToEntries match {
//          case head::tail if head.instances.=>
//
//          case _ => acc
//        }
//      }
      for ((rule, entries) <- other.instances) {
        newInstances += Pair(rule, entries ::: newInstances.getOrElse(rule, List[ScalaBlockFormatterEntry]()))
      }
      Some(new FormattingSettings(newNormalSize, newContinuationSize, newInstances, Map()))
    } else {
      None
    }
  }
}

object FormattingSettings{
  def apply(indentType: IndentType, size: Int) = indentType match {
    case IndentType.ContinuationIndent => continuationSize(size)
    case IndentType.NormalIndent => normalSize(size)
  }

  def normalSize(size: Int) = new FormattingSettings(Some(size), None, Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]](), Map())

  def continuationSize(size: Int) = new FormattingSettings(None, Some(size), Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]](), Map())

  def buildSettings(ruleInstance: ScalaFormattingRuleInstance, entries: List[ScalaBlockFormatterEntry], ruleWhiteSpaces: List[TextRange]): List[FormattingSettings] = {
    var whitespacesMap = Map[TextRange, ScalaFormattingRuleInstance]()
    for (whitespace <- ruleWhiteSpaces) {
      whitespacesMap += ((whitespace, ruleInstance))
    }
    ruleInstance.rule.getPresetIndentType match {
      case None => //don't know what type of indent this is
        List[FormattingSettings](new FormattingSettings(None, None, Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]]((ruleInstance, entries)), whitespacesMap))
      case Some(indentType) =>
        val sizeToSettings = mutable.Map[Int, FormattingSettings]()
        var settings = new FormattingSettings(None, None, Map[ScalaFormattingRuleInstance, List[ScalaBlockFormatterEntry]](), whitespacesMap)
        for (entry <- entries) {
          entry.indentInfo match {
            case Some(indentInfo) => sizeToSettings.put(indentInfo.indentLength, sizeToSettings.getOrElse(indentInfo.indentLength,
              indentType match {
                case IndentType.ContinuationIndent => continuationSize(indentInfo.indentLength)
                case IndentType.NormalIndent => normalSize(indentInfo.indentLength)
              }
            ).addInstance(ruleInstance, entry.discardInstances))
            case None => settings = settings.addInstance(ruleInstance, entry.discardInstances)
          }
        }
        settings :: sizeToSettings.values.toList
    }
  }

}
