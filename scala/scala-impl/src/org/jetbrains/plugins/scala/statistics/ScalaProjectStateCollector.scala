package org.jetbrains.plugins.scala.statistics

//noinspection ApiStatus,UnstableApiUsage
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields.{StringValidatedByRegexpReference, String => FString}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings
//noinspection ApiStatus,UnstableApiUsage
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectExt}
import org.jetbrains.plugins.scala.statistics.ScalaProjectStateCollector._
import org.jetbrains.sbt.settings.SbtSettings

import java.nio.charset.StandardCharsets
import java.nio.file.{FileSystems, Files, Path}
import java.util
import scala.jdk.CollectionConverters.{SeqHasAsJava, SetHasAsJava}
import scala.util.Using
import scala.xml.XML

//noinspection ApiStatus,UnstableApiUsage
class ScalaProjectStateCollector extends ProjectUsagesCollector {

  override def getGroup: EventLogGroup =
    Group

  override def getMetrics(project: Project): util.Set[MetricEvent] = {

    val modulesWithScala = project.modulesWithScala

    val sbtSettings = SbtSettings.getInstance(project)
    val sbtInfoEvent = modulesWithScala
      .to(LazyList)
      .flatMap(sbtSettings.getLinkedProjectSettings)
      .flatMap(settings => Option(settings.sbtVersion))
      .headOption
      .map { sbtVersion =>
        SbtInfoEvent.metric(sbtVersion)
      }

    val compilerPluginEvents = modulesWithScala
      .map(ScalaCompilerSettings.forModule)
      .flatMap(_.plugins)
      .flatMap(_.pluginJar)
      .toSet[String]
      .map(Path.of(_))
      .filterNot(Files.isDirectory(_))
      .flatMap(getScalacPluginInfo)
      .collect { case (name, version) if CompilerPluginsWhiteList.get.contains(name) =>
        CompilerPlugin.metric(name, version)
      }

    val scalaLangLevelEvents = for {
      module <- modulesWithScala
      langLevel <- module.languageLevel
    } yield ScalaLangLevelEvent.metric(langLevel.getVersion)

    (sbtInfoEvent.toSet ++ compilerPluginEvents ++ scalaLangLevelEvents.toSet).asJava
  }
}

//noinspection UnstableApiUsage
object ScalaProjectStateCollector {

  private final val Group = new EventLogGroup("scala.project.state", 1) // TODO: SCL-24479

  /**
   * See also [[org.jetbrains.plugins.scala.statistics.SbtShellCommandsUsagesCollector]]
   *
   * This event is supposed to be replaced by:
   * [[org.jetbrains.plugins.scala.statistics.SbtSettingsCollector.Events.SbtVersion]]
   * TODO: remove it in ~ 2025.3, once there are 3 full releases that use `SbtSettingsCollector`
   */
  private final val SbtInfoEvent = Group.registerEvent("sbt.info",
    StringValidatedByRegexpReference("version", "version")
  ) // TODO: SCL-24479

  //noinspection UnstableApiUsage
  private final val CompilerPlugin = Group.registerEvent("compiler.plugin",
    FString("name", CompilerPluginsWhiteList.get.toList.asJava),
    StringValidatedByRegexpReference("version", "version")
  ) // TODO: SCL-24479

  //noinspection UnstableApiUsage
  private final val ScalaLangLevelEvent = Group.registerEvent("scala.lang.level",
    StringValidatedByRegexpReference("value", "version")
  ) // TODO: SCL-24479

  private val CompilerPluginRegex = ".+_\\d+\\.\\d+(\\.\\d+)?-(\\d+\\.\\d+(\\.\\d+)?)\\.jar".r

  private def readScalacPluginName(jar: Path): Option[String] =
    if (Files.isReadable(jar))
      Using.resource(FileSystems.newFileSystem(jar, null: ClassLoader)) { fileSystem =>
        val pluginXml = fileSystem.getPath("scalac-plugin.xml")
        for {
          _ <- Option.when(Files.exists(pluginXml))(())
          content = Files.readString(pluginXml, StandardCharsets.UTF_8)
          xml = XML.loadString(content)
          pluginNameNode <- (xml \ "name").headOption
        } yield pluginNameNode.text
      }
    else
      None

  private def guessScalacPluginVersion(jar: Path): Option[String] = jar.getFileName.toString match {
    case CompilerPluginRegex(_, version, _) => Some(version)
    case _ => None
  }

  private def getScalacPluginInfo(jar: Path): Option[(String, String)] =
    for {
      name <- readScalacPluginName(jar)
      version = guessScalacPluginVersion(jar).getOrElse("Unknown")
    } yield (name, version)
}
