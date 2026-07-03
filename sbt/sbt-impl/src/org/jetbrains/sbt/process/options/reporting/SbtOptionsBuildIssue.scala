package org.jetbrains.sbt.process.options.reporting

import com.intellij.build.issue.{BuildIssue, BuildIssueQuickFix}
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable

import java.util.{List => JList}
import scala.jdk.CollectionConverters.*

private final class SbtOptionsBuildIssue(warning: SbtOptionsWarningData) extends BuildIssue {
  override def getTitle: String = warning.title

  override def getDescription: String = warning.details

  override def getQuickFixes: JList[BuildIssueQuickFix] = warning.quickFixes.asJava

  override def getNavigatable(project: Project): Navigatable = null
}
