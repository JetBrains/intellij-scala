package org.jetbrains.plugins.scala.lang.findUsages.rules

import com.intellij.openapi.project.Project
import com.intellij.usages.impl.FileStructureGroupRuleProvider
import com.intellij.usages.rules.UsageGroupingRule

final class ScalaDeclarationGroupRuleProvider extends FileStructureGroupRuleProvider {
  override def getUsageGroupingRule(project: Project): UsageGroupingRule =
    new ScalaDeclarationUsageGroupingRule(true)
}

final class ScalaDeclarationSecondLevelGroupRuleProvider extends FileStructureGroupRuleProvider {
  override def getUsageGroupingRule(project: Project): UsageGroupingRule =
    new ScalaDeclarationUsageGroupingRule(false)
}