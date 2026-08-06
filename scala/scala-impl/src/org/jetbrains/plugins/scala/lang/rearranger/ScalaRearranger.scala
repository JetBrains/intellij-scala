package org.jetbrains.plugins.scala.lang.rearranger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.{Pair, TextRange}
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.arrangement.`match`.{ArrangementSectionRule, StdArrangementEntryMatcher, StdArrangementMatchRule}
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule
import com.intellij.psi.codeStyle.arrangement.model.{ArrangementAtomMatchCondition, ArrangementCompositeMatchCondition, ArrangementMatchCondition}
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType._
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Grouping._
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier._
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order._
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens._
import com.intellij.psi.codeStyle.arrangement.std._
import com.intellij.psi.codeStyle.arrangement._
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.rearranger.RearrangerUtils._
import org.jetbrains.plugins.scala.statistics.ScalaActionUsagesCollector

import java.util
import scala.collection.immutable.HashSet
import scala.collection.mutable
import scala.jdk.CollectionConverters._

class ScalaRearranger extends Rearranger[ScalaArrangementEntry] with ArrangementStandardSettingsAware {
  override def parseWithNew(
    root:     PsiElement,
    document: Document,
    ranges:   util.Collection[_ <: TextRange],
    element:  PsiElement,
    settings: ArrangementSettings
  ): Pair[ScalaArrangementEntry, util.List[ScalaArrangementEntry]] = {
    val groupingRules = getGroupingRules(settings)

    val existingInfo = new ScalaArrangementParseInfo
    root.accept(new ScalaArrangementVisitor(existingInfo, document, ranges.asScala, groupingRules))

    val newInfo = new ScalaArrangementParseInfo
    element.accept(new ScalaArrangementVisitor(newInfo, document, Iterable(element.getTextRange), groupingRules))
    if (newInfo.entries.size != 1) {
      null
    } else {
      Pair.create(newInfo.entries.head, existingInfo.entries.asJava)
    }
  }

  override def parse(
    root:     PsiElement,
    document: Document,
    ranges:   util.Collection[_ <: TextRange],
    settings: ArrangementSettings
  ): util.List[ScalaArrangementEntry] = {
    ScalaActionUsagesCollector.logRearrange(root.getProject)
    val info = new ScalaArrangementParseInfo
    root.accept(new ScalaArrangementVisitor(info, document, ranges.asScala, getGroupingRules(settings)))
    if (settings != null) {
      settings.getGroupings.forEach { rule =>
        if (DEPENDENT_METHODS == rule.getGroupingType) {
          setupUtilityMethods(info, rule.getOrderType)
        } else if (JAVA_GETTERS_AND_SETTERS == rule.getGroupingType) {
          setupJavaGettersAndSetters(info)
        } else if (SCALA_GETTERS_AND_SETTERS == rule.getGroupingType) {
          setupScalaGettersAndSetters(info)
        }
      }
    }
    info.entries.asJava
  }

  override def getBlankLines(settings: CodeStyleSettings, parent: ScalaArrangementEntry, previous:
  ScalaArrangementEntry, target: ScalaArrangementEntry): Int = {
    if (previous == null) {
      -1
    } else {
      val codeStyleSettings = settings.getCommonSettings(ScalaLanguage.INSTANCE) //probably this will not work

      def getBlankLines(typeAround: ArrangementSettingsToken) =
        if (typeAround == VAL || typeAround == VAR || typeAround == TYPE) codeStyleSettings.BLANK_LINES_AROUND_FIELD
        else if (typeAround == FUNCTION || typeAround == MACRO) codeStyleSettings.BLANK_LINES_AROUND_METHOD
        else if (typeAround == CLASS || typeAround == TRAIT || typeAround == OBJECT) {
          codeStyleSettings.BLANK_LINES_AROUND_CLASS
        } else -1

      val targetType = target.innerEntryType.getOrElse(target.getType)
      val previousType = previous.innerEntryType.getOrElse(previous.getType)
      Math.max(getBlankLines(targetType), getBlankLines(previousType))
    }
  }

  private def getGroupingRules(settings: ArrangementSettings): Set[ArrangementSettingsToken] = {
    var result = HashSet[ArrangementSettingsToken]()
    if (settings != null) {
      settings.getGroupings.forEach { rule =>
        result = result + rule.getGroupingType
      }
    }
    result
  }

  override def getDefaultSettings: StdArrangementSettings = ScalaRearranger.defaultSettings

  override def getSerializer: ArrangementSettingsSerializer = ScalaRearranger.SETTINGS_SERIALIZER

  override def getSupportedGroupingTokens: util.List[CompositeArrangementSettingsToken] =
    List(new CompositeArrangementSettingsToken(DEPENDENT_METHODS, BREADTH_FIRST, DEPTH_FIRST),
      new CompositeArrangementSettingsToken(JAVA_GETTERS_AND_SETTERS),
      new CompositeArrangementSettingsToken(SCALA_GETTERS_AND_SETTERS),
      new CompositeArrangementSettingsToken(SPLIT_INTO_UNARRANGEABLE_BLOCKS_BY_EXPRESSIONS),
      new CompositeArrangementSettingsToken(SPLIT_INTO_UNARRANGEABLE_BLOCKS_BY_IMPLICITS)
    ).asJava

  override def getSupportedMatchingTokens: util.List[CompositeArrangementSettingsToken] =
    List(
      new CompositeArrangementSettingsToken(General.TYPE, scalaTypesValues.asJavaCollection),
      new CompositeArrangementSettingsToken(General.MODIFIER, scalaModifiers.asJavaCollection),
      new CompositeArrangementSettingsToken(General.ORDER, Order.KEEP, Order.BY_NAME)).asJava

  override def isEnabled(token: ArrangementSettingsToken, current: ArrangementMatchCondition): Boolean =
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

  override def getMutexes: util.List[util.Set[ArrangementSettingsToken]] =
    List(scalaAccessModifiers.asJava, scalaTypesValues.asJava).asJava

  private def setupUtilityMethods(info: ScalaArrangementParseInfo, orderType: ArrangementSettingsToken): Unit = {
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

  private def setupDepthFirstDependency(info: ScalaArrangementDependency): Unit = {
    for (dependency <- info.getDependentMethodInfos) {
      setupDepthFirstDependency(dependency)
      val dependentEntry = dependency.getAnchorMethod
      if (dependentEntry != null && dependentEntry.getDependencies == null) {
        val method = info.getAnchorMethod
        if (method != null) {
          dependentEntry.addDependency(method)
        }
      }
    }
  }

  private def setupBreadthFirstDependency(info: ScalaArrangementDependency): Unit = {
    val toProcess = mutable.Queue[ScalaArrangementDependency]()
    toProcess += info
    while (toProcess.nonEmpty) {
      val current = toProcess.dequeue()
      for (dependencyInfo <- current.getDependentMethodInfos) {
        val dependencyMethod = dependencyInfo.getAnchorMethod
        if (dependencyMethod != null && dependencyMethod.getDependencies == null) {
          val method = current.getAnchorMethod
          if (method != null) {
            dependencyMethod.addDependency(method)
          }
        }
        toProcess += dependencyInfo
      }
    }
  }

  private def setupJavaGettersAndSetters(info: ScalaArrangementParseInfo): Unit =
    setupGettersAndSetters(info.javaProperties)

  private def setupScalaGettersAndSetters(info: ScalaArrangementParseInfo): Unit =
    setupGettersAndSetters(info.scalaProperties)

  private def setupGettersAndSetters(properties: Iterable[ScalaPropertyInfo]): Unit = {
    for (propertyInfo <- properties) {
      if (propertyInfo.isComplete && propertyInfo.setter.getDependencies == null) {
        propertyInfo.setter.addDependency(propertyInfo.getter)
      }
    }
  }
}

object ScalaRearranger {

  private def rule(tokens: ArrangementSettingsToken*): ArrangementSectionRule = {
    val conditions = tokens.map(t => new ArrangementAtomMatchCondition(t, t))
    val condition = if (conditions.length == 1) {
      conditions.head
    } else {
      new ArrangementCompositeMatchCondition(conditions.asJava)
    }
    val matcher = new StdArrangementEntryMatcher(condition)
    val matchRule = new StdArrangementMatchRule(matcher)
    ArrangementSectionRule.create(matchRule)
  }

  private def getDefaultSettings: StdArrangementSettings = {

    val groupingRules = List[ArrangementGroupingRule](
      new ArrangementGroupingRule(DEPENDENT_METHODS, DEPTH_FIRST),
      new ArrangementGroupingRule(JAVA_GETTERS_AND_SETTERS),
      new ArrangementGroupingRule(SCALA_GETTERS_AND_SETTERS),
      new ArrangementGroupingRule(SPLIT_INTO_UNARRANGEABLE_BLOCKS_BY_IMPLICITS)
    )

    val accessModifiers = scalaAccessModifiers

    var matchRules = mutable.Buffer.empty[ArrangementSectionRule]
    matchRules += rule(OBJECT, IMPLICIT)
    matchRules ++= accessModifiers.map(access => rule(FUNCTION, access, IMPLICIT))
    matchRules += rule(CLASS, IMPLICIT)

    matchRules ++= accessModifiers.map(access => rule(TYPE, access, FINAL))
    matchRules ++= accessModifiers.map(access => rule(TYPE, access))

    matchRules ++= accessModifiers.map(access => rule(VAL, access, FINAL, LAZY))
    matchRules ++= accessModifiers.map(access => rule(VAL, access, FINAL))
    matchRules ++= accessModifiers.map(access => rule(VAL, access, LAZY))
    matchRules += rule(VAL, ABSTRACT)
    matchRules += rule(VAL, RearrangerUtils.OVERRIDE)
    matchRules ++= accessModifiers.map(access => rule(VAL, access))

    matchRules ++= accessModifiers.map(access => rule(VAR, access, RearrangerUtils.OVERRIDE))
    matchRules ++= accessModifiers.map(access => rule(VAR, access))

    matchRules ++= accessModifiers.map(access => rule(CONSTRUCTOR, access))
    matchRules += rule(CONSTRUCTOR)

    matchRules += rule(FUNCTION, PUBLIC, FINAL, RearrangerUtils.OVERRIDE)
    matchRules += rule(FUNCTION, PROTECTED, FINAL, RearrangerUtils.OVERRIDE)
    matchRules ++= accessModifiers.map(access => rule(FUNCTION, access))

    //TODO: Is 'override' ok with macros?
    matchRules += rule(MACRO, PUBLIC, RearrangerUtils.OVERRIDE)
    matchRules += rule(MACRO, PROTECTED, RearrangerUtils.OVERRIDE)

    matchRules ++= accessModifiers.map(access => rule(MACRO, access))
    matchRules ++= accessModifiers.map(access => rule(TRAIT, access, ABSTRACT, SEALED))
    matchRules ++= accessModifiers.map(access => rule(TRAIT, access, ABSTRACT))
    matchRules ++= accessModifiers.map(access => rule(TRAIT, access, SEALED))
    matchRules ++= accessModifiers.map(access => rule(TRAIT, access))

    matchRules ++= accessModifiers.map(access => rule(CLASS, access, ABSTRACT, SEALED))
    matchRules ++= accessModifiers.map(access => rule(CLASS, access, ABSTRACT))
    matchRules ++= accessModifiers.map(access => rule(CLASS, access, SEALED))
    matchRules ++= accessModifiers.map(access => rule(CLASS, access))

    matchRules ++= accessModifiers.map(access => rule(OBJECT, access))

    new StdArrangementSettings(groupingRules.asJava, matchRules.asJava)
  }

  private val defaultSettings = getDefaultSettings

  private val SETTINGS_SERIALIZER = new DefaultArrangementSettingsSerializer(new ScalaSettingsSerializerMixin(), defaultSettings)

  private class ScalaSettingsSerializerMixin extends DefaultArrangementSettingsSerializer.Mixin {
    override def deserializeToken(id: String): ArrangementSettingsToken = getTokenById(id).orNull
  }
}