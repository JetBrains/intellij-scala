package org.jetbrains.plugins.scala.internal

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module
import com.intellij.openapi.project.Project
import com.intellij.troubleshooting.GeneralTroubleInfoCollector
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.internal.ScalaGeneralTroubleInfoCollector.Log
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectExt}

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

    val uniqueScalaVersionsText = scalaVersionToModules
      .keys.toSeq
      .distinct.sorted.reverse
      .map { version =>
        val numberOfModulesWithVersion = scalaVersionToModules.getOrElse(version, Nil).size
        val numberOfModulesSuffix = s"${if (numberOfModulesWithVersion > 1) s" ($numberOfModulesWithVersion)" else ""}"
        s"${version.minor}$numberOfModulesSuffix"
      }
      .mkString(", ")

    val result = new StringBuilder
    if (uniqueScalaVersionsText.nonEmpty) {
      result.append(s"Scala versions: $uniqueScalaVersionsText")
      result.append("\n")
    }

    //NOTE: we generally expect a single SBT version, but still using sequence, just in case...
    //Who knows what configurations there might be in projects...
    val sbtVersions = buildModules.flatMap(_.sbtVersion)
    val sbtVersionsText = sbtVersions.map(_.toString).mkString(", ")
    if (sbtVersionsText.nonEmpty) {
      result.append(s"SBT version: $sbtVersionsText")
      result.append("\n")
    }

    result.toString.trim
  } catch {
    case NonFatal(ex) =>
      //In case of unexpected exceptions, don't want to block platform from collecting troubleshoot information
      Log.error(ex)
      ""
  }
}

object ScalaGeneralTroubleInfoCollector {
  private val Log = Logger.getInstance(classOf[ScalaGeneralTroubleInfoCollector])
}
