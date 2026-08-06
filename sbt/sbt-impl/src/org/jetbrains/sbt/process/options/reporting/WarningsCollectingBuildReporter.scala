package org.jetbrains.sbt.process.options.reporting

import com.intellij.build.FilePosition
import com.intellij.build.issue.BuildIssue
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.build.NoOpBuildReporter

import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.*

private[sbt] final class WarningsCollectingBuildReporter extends NoOpBuildReporter {

  private val warnings = ArrayBuffer.empty[SbtOptionsWarningData]

  def collectedWarnings: Seq[SbtOptionsWarningData] = warnings.toSeq

  override def warning(@Nls message: String, position: Option[FilePosition]): Unit =
    warnings += SbtOptionsWarningData(message, message)

  override def warning(@Nls message: String, position: Option[FilePosition], @Nls details: String): Unit =
    warnings += SbtOptionsWarningData(message, details)

  override def warning(issue: BuildIssue): Unit =
    warnings += SbtOptionsWarningData(issue.getTitle, issue.getDescription, issue.getQuickFixes.asScala.toSeq)

  override def warning(
    @Nls message: String,
    position: Option[FilePosition],
    @Nls details: String,
    navigatable: Option[Navigatable]
  ): Unit =
    warnings += SbtOptionsWarningData(message, details)
}
