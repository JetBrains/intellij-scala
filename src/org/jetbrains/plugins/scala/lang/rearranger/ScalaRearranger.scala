package org.jetbrains.plugins.scala
package lang.rearranger

import com.intellij.internal.statistic.UsageTrigger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.{Pair, TextRange}
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.arrangement.{ArrangementSettings, _}
import com.intellij.psi.codeStyle.arrangement.`match`.{ArrangementSectionRule, StdArrangementEntryMatcher, StdArrangementMatchRule}
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule
import com.intellij.psi.codeStyle.arrangement.model.{ArrangementAtomMatchCondition, ArrangementCompositeMatchCondition, ArrangementMatchCondition}
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType._
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Grouping._
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier._
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order._
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens._
import com.intellij.psi.codeStyle.arrangement.std._

import scala.collection.JavaConversions._
import scala.collection.{immutable, mutable}

/**
 * @author Roman.Shein
 *         Date: 08.07.13
 */
class ScalaRearranger extends Rearranger[ScalaArrangementEntry] with ArrangementStandardSettingsAware {

  override def parseWithNew(root: PsiElement, document: Document, ranges: java.util.Collection[TextRange],
                                      element: PsiElement, settings: ArrangementSettings): Pair[ScalaArrangementEntry, java.util.List[ScalaArrangementEntry]] = {
    val groupingRules = getGroupingRules(settings)

    val existingInfo = new ScalaArrangementParseInfo
    root.accept(new ScalaArrangementVisitor(existingInfo, document, collectionAsScalaIterable(ranges), groupingRules))

    val newInfo = new ScalaArrangementParseInfo
    element.accept(new ScalaArrangementVisitor(newInfo, document, Iterable(element.getTextRange), groupingRules))
    if (newInfo.entries.size != 1) {
      null
    } else {
      Pair.create(newInfo.entries(0), existingInfo.entries)
    }
  }

  override def parse(root: PsiElement, document: Document,
                              ranges: java.util.Collection[TextRange], settings: ArrangementSettings) = {
    UsageTrigger.trigger(ScalaRearranger.featureId)
    val info = new ScalaArrangementParseInfo
    root.accept(new ScalaArrangementVisitor(info, document, ranges, getGroupingRules(settings)))
    if (settings != null) {
      for (rule <- settings.getGroupings) {
        if (DEPENDENT_METHODS == rule.getGroupingType) {
          setupUtilityMethods(info, rule.getOrderType)
        } else if (JAVA_GETTERS_AND_SETTERS == rule.getGroupingType) {
          setupJavaGettersAndSetters(info)
        } else if (SCALA_GETTERS_AND_SETTERS == rule.getGroupingType) {
          setupScalaGettersAndSetters(info)
        }
      }
    }
    info.entries
  }

  override def getBlankLines(settings: CodeStyleSettings, parent: ScalaArrangementEntry, previous:
  ScalaArrangementEntry, target: ScalaArrangementEntry): Int = {
    if (previous == null) {
      -1
    } else {
      val codeStyleSettings = settings.getCommonSettings(ScalaFileType.SCALA_LANGUAGE) //probably this will not work
      val targetType = target.getType
      if (targetType == VAL || targetType == VAR || targetType == TYPE) {
        codeStyleSettings.BLANK_LINES_AROUND_FIELD
      } else if (targetType == FUNCTION || targetType == MACRO) {
        codeStyleSettings.BLANK_LINES_AROUND_METHOD
      } else if (targetType == CLASS || targetType == TRAIT) {
        codeStyleSettings.BLANK_LINES_AROUND_CLASS
      } else {
        -1
      }
    }
  }

  private def getGroupingRules(settings: ArrangementSettings) = {
    var result = immutable.HashSet[ArrangementSettingsToken]()
    if (settings != null) {
      for (rule <- settings.getGroupings) {
        result = result + rule.getGroupingType
      }
    }
    result
  }

  override def getDefaultSettings = ScalaRearranger.defaultSettings

  override def getSerializer = ScalaRearranger.SETTINGS_SERIALIZER

  override def getSupportedGroupingTokens =
    seqAsJavaList(immutable.List(new CompositeArrangementSettingsToken(DEPENDENT_METHODS, BREADTH_FIRST, DEPTH_FIRST),
      new CompositeArrangementSettingsToken(JAVA_GETTERS_AND_SETTERS),
      new CompositeArrangementSettingsToken(SCALA_GETTERS_AND_SETTERS),
      new CompositeArrangementSettingsToken(SPLIT_INTO_UNARRANGEABLE_BLOCKS_BY_EXPRESSIONS)
    ))

  override def getSupportedMatchingTokens =
    seqAsJavaList(immutable.List(new CompositeArrangementSettingsToken(General.TYPE,
      scalaTypesValues.toList), new CompositeArrangementSettingsToken(General.MODIFIER, scalaModifiers.toList),
      new CompositeArrangementSettingsToken(General.ORDER, Order.KEEP, Order.BY_NAME)))

  override def isEnabled(token: ArrangementSettingsToken, current: ArrangementMatchCondition) =
    (scalaTypesValues.contains(token) || supportedOrders.contains(token)) ||
            (if (current != null) {
              val tokenType = ArrangementUtil.parseType(current)
              if (tokenType != null) {
                tokensForType(tokenType).contains(token)
              } else {
                commonModifiers.contains(token)
              }
            } else {
              commonModifiers.contains(token)
            })

  override def buildMatcher(condition: ArrangementMatchCondition) = throw new IllegalArgumentException("Can't build a matcher for condition " + condition)

  override def getMutexes = seqAsJavaList(immutable.List(scalaAccessModifiersValues, scalaTypesValues))

  private def setupUtilityMethods(info: ScalaArrangementParseInfo, orderType: ArrangementSettingsToken) {
    if (DEPTH_FIRST == orderType) {
      for (root <- info.getMethodDependencyRoots) {
        setupDepthFirstDependency(root)
      }
    }
    else if (BREADTH_FIRST == orderType) {
      for (root <- info.getMethodDependencyRoots) {
        setupBreadthFirstDependency(root)
      }
    }
    else {
      assert(assertion = false, orderType)
    }
  }

  private def setupDepthFirstDependency(info: ScalaArrangementDependency) {
    for (dependency <- info.getDependentMethodInfos) {
      setupDepthFirstDependency(dependency)
      val dependentEntry = dependency.getAnchorMethod
      if (dependentEntry.getDependencies == null) {
        dependentEntry.addDependency(info.getAnchorMethod)
      }
    }
  }

  private def setupBreadthFirstDependency(info: ScalaArrangementDependency) {
    val toProcess = mutable.Queue[ScalaArrangementDependency]()
    toProcess += info
    while (!toProcess.isEmpty) {
      val current = toProcess.dequeue()
      for (dependencyInfo <- current.getDependentMethodInfos) {
        val dependencyMethod = dependencyInfo.getAnchorMethod
        if (dependencyMethod.getDependencies == null) {
          dependencyMethod.addDependency(current.getAnchorMethod)
        }
        toProcess += dependencyInfo
      }
    }
  }

  private def setupJavaGettersAndSetters(info: ScalaArrangementParseInfo) = setupGettersAndSetters(info.javaProperties)

  private def setupScalaGettersAndSetters(info: ScalaArrangementParseInfo) = setupGettersAndSetters(info.scalaProperties)

  private def setupGettersAndSetters(properties: Iterable[ScalaPropertyInfo]) {
    for (propertyInfo <- properties) {
      if (propertyInfo.isComplete && propertyInfo.setter.getDependencies == null) {
        propertyInfo.setter.addDependency(propertyInfo.getter)
      }
    }
  }
}

object ScalaRearranger {

  private val featureId = "scala.rearrange"

  private def addCondition(matchRules: immutable.List[ArrangementSectionRule], conditions: ArrangementSettingsToken*) = {
    if (conditions.length == 1) {
      ArrangementSectionRule.create(
        new StdArrangementMatchRule(
          new StdArrangementEntryMatcher(new ArrangementAtomMatchCondition(conditions(0), conditions(0)))
        )
      ) :: matchRules
    } else {
      val composite = new ArrangementCompositeMatchCondition
      for (condition <- conditions) {
        composite.addOperand(new ArrangementAtomMatchCondition(condition, condition))
      }
      ArrangementSectionRule.create(new StdArrangementMatchRule(new StdArrangementEntryMatcher(composite))) :: matchRules
    }
  }

  private def getDefaultSettings = {
    val groupingRules = immutable.List[ArrangementGroupingRule](new ArrangementGroupingRule(DEPENDENT_METHODS, DEPTH_FIRST), new ArrangementGroupingRule(JAVA_GETTERS_AND_SETTERS),
      new ArrangementGroupingRule(SCALA_GETTERS_AND_SETTERS))
    var matchRules = immutable.List[ArrangementSectionRule]()
    for (access <- scalaAccessModifiersValues) {
      matchRules = addCondition(matchRules, TYPE, access, FINAL)
    }
    for (access <- scalaAccessModifiersValues) {
      matchRules = addCondition(matchRules, TYPE, access)
    }
//    matchRules = addCondition(matchRules, TYPE)
    for (access <- scalaAccessModifiersValues) {
      matchRules = addCondition(matchRules, VAL, access, FINAL, LAZY)
    }
    for (access <- scalaAccessModifiersValues) {
      matchRules = addCondition(matchRules, VAL, access, FINAL)
    }
    for (access <- scalaAccessModifiersValues) {
      matchRules = addCondition(matchRules, VAL, access, LAZY)
    }
    matchRules = addCondition(matchRules, VAL, ABSTRACT)
    matchRules = addCondition(matchRules, VAL, OVERRIDE)
    for (access <- scalaAccessModifiersValues) {
      matchRules = addCondition(matchRules, VAL, access)
    }
//    matchRules = addCondition(matchRules, VAL)
    for (access <- scalaAccessModifiersValues) {
      matchRules = addCondition(matchRules, VAR, access, OVERRIDE)
    }
    for (access <- scalaAccessModifiersValues) {
      matchRules = addCondition(matchRules, VAR, access)
    }
//    matchRules = addCondition(matchRules, VAR)
    for (access <- scalaAccessModifiersValues) {
      matchRules = addCondition(matchRules, CONSTRUCTOR, access)
    }
    matchRules = addCondition(matchRules, CONSTRUCTOR)
    matchRules = addCondition(matchRules, FUNCTION, PUBLIC, FINAL, OVERRIDE, IMPLICIT)
    matchRules = addCondition(matchRules, FUNCTION, PROTECTED, FINAL, OVERRIDE, IMPLICIT)
    for (access <- scalaAccessModifiersValues) {
      matchRules = addCondition(matchRules, FUNCTION, PUBLIC, FINAL, IMPLICIT)
    }
    matchRules = addCondition(matchRules, FUNCTION, PUBLIC, FINAL, OVERRIDE)
    matchRules = addCondition(matchRules, FUNCTION, PROTECTED, FINAL, OVERRIDE)
    for (access <- scalaAccessModifiersValues) {
      matchRules = addCondition(matchRules, FUNCTION, access)
    }
//    matchRules = addCondition(matchRules, FUNCTION)
    matchRules = addCondition(matchRules, MACRO, PUBLIC, OVERRIDE)
    matchRules = addCondition(matchRules, MACRO, PROTECTED, OVERRIDE)
    for (access <- scalaAccessModifiersValues) {
      matchRules = addCondition(matchRules, MACRO, access)
    }
//    matchRules = addCondition(matchRules, MACRO)
    for (access <- scalaAccessModifiersValues) {
      matchRules = addCondition(matchRules, TRAIT, access, ABSTRACT, SEALED)
    }
    for (access <- scalaAccessModifiersValues) {
      matchRules = addCondition(matchRules, TRAIT, access, ABSTRACT)
    }
    for (access <- scalaAccessModifiersValues) {
      matchRules = addCondition(matchRules, TRAIT, access, SEALED)
    }
    for (access <- scalaAccessModifiersValues) {
      matchRules = addCondition(matchRules, TRAIT, access)
    }
//    matchRules = addCondition(matchRules, TRAIT)
    for (access <- scalaAccessModifiersValues) {
      matchRules = addCondition(matchRules, CLASS, access, ABSTRACT, SEALED)
    }
    for (access <- scalaAccessModifiersValues) {
      matchRules = addCondition(matchRules, CLASS, access, ABSTRACT)
    }
    for (access <- scalaAccessModifiersValues) {
      matchRules = addCondition(matchRules, CLASS, access, SEALED)
    }
    for (access <- scalaAccessModifiersValues) {
      matchRules = addCondition(matchRules, CLASS, access)
    }
//    matchRules = addCondition(matchRules, CLASS)
    //TODO: Is 'override' ok for macros?

    new StdArrangementSettings(groupingRules, matchRules.reverse)
  }

  private val defaultSettings = getDefaultSettings

  private val SETTINGS_SERIALIZER = new DefaultArrangementSettingsSerializer(new ScalaSettingsSerializerMixin(), defaultSettings)

}