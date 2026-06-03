package org.jetbrains.plugins.scala.compiler.sync

import com.intellij.build.issue.{BuildIssue, BuildIssueQuickFix}
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.plugins.scala.compiler.CompilerIntegrationBundle

//noinspection ApiStatus,UnstableApiUsage
private final class MissingScalaSdkBuildIssue(moduleNames: Seq[String], quickFix: Option[BuildIssueQuickFix]) extends BuildIssue {
  override def getTitle: String = moduleNames match {
    case Seq() => CompilerIntegrationBundle.message("missing.scala.sdk.build.issue.title.no.module")
    case modules =>
      val quoted = modules.map(quotedName).mkString(", ")
      CompilerIntegrationBundle.message("missing.scala.sdk.build.issue.title.modules", modules.length, quoted)
  }

  override def getDescription: String = {
    val moduleDescription =
      if (moduleNames.isEmpty) CompilerIntegrationBundle.message("missing.scala.sdk.build.issue.description.no.module")
      else {
        val bracketed = moduleNames.map(bracketedName).mkString(", ")
        CompilerIntegrationBundle.message("missing.scala.sdk.build.issue.description.modules", moduleNames.length, bracketed)
      }
    val misconfiguration = CompilerIntegrationBundle.message("missing.scala.sdk.build.issue.description.misconfiguration")
    val syncSuggestion = quickFix match {
      case Some(fix) => CompilerIntegrationBundle.message("missing.scala.sdk.build.issue.description.sync.quick.fix", fix.getId)
      case None => CompilerIntegrationBundle.message("missing.scala.sdk.build.issue.description.check.project.structure")
    }
    Seq(moduleDescription, misconfiguration, syncSuggestion).mkString("\n")
  }

  override def getQuickFixes: java.util.List[BuildIssueQuickFix] = quickFix match {
    case Some(fix) => java.util.List.of(fix)
    case None => java.util.Collections.emptyList()
  }

  override def getNavigatable(project: Project): Navigatable = null

  private def quotedName(name: String): String = s"'$name'"

  private def bracketedName(name: String): String = s"[$name]"
}
