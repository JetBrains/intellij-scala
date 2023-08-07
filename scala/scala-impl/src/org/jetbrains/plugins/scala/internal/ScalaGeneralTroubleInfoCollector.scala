package org.jetbrains.plugins.scala.internal

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module
import com.intellij.openapi.project.Project
import com.intellij.troubleshooting.GeneralTroubleInfoCollector
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.internal.ScalaGeneralTroubleInfoCollector.Log
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectExt, Version}

import scala.util.control.NonFatal

/**
 * Example of output: {{{
 *   === Scala ===
 *   Scala versions: 3.3.0, 2.13.11 (3)
 *   SBT version: 1.9.3
 * }}}
 *
 * See also [[org.jetbrains.plugins.scala.compiler.actions.internal.ScalaCollectShortTroubleshootingInfoAction]]
 */
final class ScalaGeneralTroubleInfoCollector extends GeneralTroubleInfoCollector {

  override def getTitle: String = "Scala"

  override def collectInfo(project: Project): String = try {
    val modulesAll = project.modules
    val (buildModules, mainModules) = modulesAll.partition(_.isBuildModule)

    val scalaVersionToModules: Map[ScalaVersion, Seq[module.Module]] =
      mainModules.groupBy(_.scalaMinorVersion).collect { case Some(v) -> modules => v -> modules }

    val sbtVersionToModules: Map[Version, Seq[module.Module]] =
      buildModules.groupBy(_.sbtVersion).collect { case Some(v) -> modules => v -> modules }

    ScalaGeneralTroubleInfoCollector.buildText(
      scalaVersionToModules,
      sbtVersionToModules
    )
  } catch {
    case NonFatal(ex) =>
      //In case of unexpected exceptions, don't want to block platform from collecting troubleshoot information
      Log.error(ex)
      ""
  }
}

object ScalaGeneralTroubleInfoCollector {

  private val Log = Logger.getInstance(classOf[ScalaGeneralTroubleInfoCollector])

  @VisibleForTesting
  def buildText(
    scalaVersionToModules: Map[ScalaVersion, Seq[_]],
    sbtVersionToModules: Map[Version, Seq[_]]
  ): String = {
    val result = new StringBuilder

    val uniqueScalaVersionsText = buildKeysTextWithNumberOfOccurrences(scalaVersionToModules)(_.minor)
    if (uniqueScalaVersionsText.nonEmpty) {
      result.append(s"Scala versions: $uniqueScalaVersionsText")
      result.append("\n")
    }

    val sbtVersionsText = buildKeysTextWithNumberOfOccurrences(sbtVersionToModules)(_.toString)
    if (sbtVersionsText.nonEmpty) {
      result.append(s"SBT version: $sbtVersionsText")
      result.append("\n")
    }

    result.toString.trim
  }

  /**
   * @example {{{
   *   input: Map(2.13 -> Seq(a, b, c), 2.11 -> Seq(c), 2.12 -> Seq(d, e)
   *   output: 2.13 (3), 2.12 (2), 2.11
   * }}}
   */
  private def buildKeysTextWithNumberOfOccurrences[T: Ordering, MyModule](
    map: Map[T, Seq[MyModule]]
  )(present: T => String): String = {
    val keyToOccurrences: Seq[(T, Int)] = map.view
      .mapValues(_.size)
      .toSeq
      .sortBy(_._1)
      .reverse
    keyToOccurrences
      .map { case (key, occurrencesCount) =>
        val occurrencesText = s"${if (occurrencesCount > 1) s" ($occurrencesCount)" else ""}"
        s"${present(key)}$occurrencesText"
      }
      .mkString(", ")
  }
}
