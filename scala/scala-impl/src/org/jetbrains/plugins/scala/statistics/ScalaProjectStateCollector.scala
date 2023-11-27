package org.jetbrains.plugins.scala.statistics

//noinspection ApiStatus,UnstableApiUsage
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields.{StringValidatedByRegexpReference, String => FString}
//noinspection ApiStatus,UnstableApiUsage
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectExt}
import org.jetbrains.plugins.scala.statistics.ScalaProjectStateCollector._
import org.jetbrains.sbt.settings.SbtSettings

import java.io.File
import java.util
import java.util.zip.ZipFile
import scala.io.Source
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

    val compilerConfiguration = ScalaCompilerConfiguration.instanceIn(project)
    val compilerPluginEvents = modulesWithScala
      .map(compilerConfiguration.getSettingsForModule)
      .flatMap(_.plugins)
      .toSet[String]
      .map(new File(_))
      .filterNot(_.isDirectory)
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

object ScalaProjectStateCollector {

  private final val Group = new EventLogGroup("scala.project.state", 1)

  //noinspection UnstableApiUsage
  private final val SbtInfoEvent = Group.registerEvent("sbt.info",
    StringValidatedByRegexpReference("version", "version")
  )

  //noinspection UnstableApiUsage
  private final val CompilerPlugin = Group.registerEvent("compiler.plugin",
    FString("name", CompilerPluginsWhiteList.get.toList.asJava),
    StringValidatedByRegexpReference("version", "version")
  )

  //noinspection UnstableApiUsage
  private final val ScalaLangLevelEvent = Group.registerEvent("scala.lang.level",
    StringValidatedByRegexpReference("value", "version")
  )

  private val CompilerPluginRegex = ".+_\\d+\\.\\d+(\\.\\d+)?-(\\d+\\.\\d+(\\.\\d+)?)\\.jar".r

  private def readScalacPluginName(jar: File): Option[String] =
    if (jar.canRead)
      Using.resource(new ZipFile(jar)) { zipFile =>
        for {
          entry <- Option(zipFile.getEntry("scalac-plugin.xml"))
          content = Using.resource(Source.fromInputStream(zipFile.getInputStream(entry)))(_.mkString)
          xml = XML.loadString(content)
          pluginNameNode <- (xml \ "name").headOption
        } yield pluginNameNode.text
      }
    else
      None

  private def guessScalacPluginVersion(jar: File): Option[String] = jar.getName match {
    case CompilerPluginRegex(_, version, _) => Some(version)
    case _ => None
  }

  private def getScalacPluginInfo(jar: File): Option[(String, String)] =
    for {
      name <- readScalacPluginName(jar)
      version = guessScalacPluginVersion(jar).getOrElse("Unknown")
    } yield (name, version)
}
