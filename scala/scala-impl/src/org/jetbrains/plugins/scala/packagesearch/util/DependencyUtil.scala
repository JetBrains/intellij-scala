package org.jetbrains.plugins.scala.packagesearch.util

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{ObjectExt, SeqExt}
import org.jetbrains.plugins.scala.project.{ProjectExt, ProjectPsiElementExt}

object DependencyUtil {

  // heuristic similar to what coursier does
  def isStable(version: String): Boolean =
    !version.toLowerCase.endsWith("snapshot") &&
      !version.exists(_.isLetter) &&
      version
        .split(Array('.', '-'))
        .forall(_.lengthCompare(5) <= 0)

  /**
   * Append scala version suffix to `artifactId`. If `fullVersion = false`, then:
   *  - for Scala 3 use `artifactId_3`
   *  - for Scala 2 use `artifactId_2.x` (2.13, 2.12, etc.)
   */
  def buildScalaArtifactIdString(artifactId: String, scalaVersion: String, fullVersion: Boolean): String = {
    if (scalaVersion == null || scalaVersion.isEmpty) artifactId
    else {
      val paddedScalaVersion = scalaVersion.split('.').padTo(3, "0")
      val partsToTake =
        if (fullVersion) 3
        else if (paddedScalaVersion.head == "3") 1
        else 2
      val versionString = paddedScalaVersion.take(partsToTake).mkString(".")
      s"${artifactId}_$versionString"
    }
  }

  def getAllScalaVersionsOrDefault(element: PsiElement, majorOnly: Boolean = false): Seq[String] = {
    val projectVersions = element.getProject.allScalaVersions.sort(reverse = true)
    
    if (projectVersions.isEmpty) {
      val langLevel = element.scalaLanguageLevelOrDefault
      val version = langLevel.getVersion.pipeIf(!majorOnly)(_ + ".0")
      List(version)
    } else {
      val versionStrings =
        if (majorOnly) projectVersions.map(_.major)
        else projectVersions.map(_.minor)
      versionStrings.distinct
    }
  }
}
