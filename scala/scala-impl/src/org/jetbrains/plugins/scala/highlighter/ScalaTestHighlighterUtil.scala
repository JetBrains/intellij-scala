package org.jetbrains.plugins.scala.highlighter

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

object ScalaTestHighlighterUtil {

  private val scalaTestKeywords =
    Set("in", "ignore", "is", "be", "taggedAs", "when", "that", "which", "must", "can", "should", "behave", "feature",
      "scenario", "like", "pending", "it", "they", "behavior", "describe", "property", "test")

  //TODO it is possible for this to create some false-positives, but it is very unlikely
  def isHighlightableScalaTestKeyword(classFqn: String, methodName: String, project: Project): Boolean =
    classFqn != null && ScalaProjectSettings.getInstance(project).isCustomScalatestSyntaxHighlighting &&
      classFqn.startsWith("org.scalatest") && scalaTestKeywords.contains(methodName)
}
