package org.jetbrains.jps.incremental.scala.data

import java.io.File

import org.jetbrains.jps.incremental.scala.SettingsManager
import org.jetbrains.jps.model.JpsProject

import scala.collection.JavaConverters._

/**
  * @author Maris Alexandru
  */
class HydraData(project: JpsProject, files: List[File], scalaVersion: String) {
  private val HydraCompilerRegex = s".*scala-compiler-$scalaVersion-hydra\\d+\\.jar".r
  private val HydraReflectRegex = s".*scala-reflect-$scalaVersion-hydra\\d+\\.jar".r
  private val HydraBridgeRegex = s".*${HydraData.HydraBridgeName}-${SettingsManager.getHydraSettings(project).getHydraVersion}-sources.jar".r

  def getCompilerJar: Option[File] = files.find(file => HydraCompilerRegex.findFirstIn(file.getName).nonEmpty)

  def getReflectJar: Option[File] = files.find(file => HydraReflectRegex.findFirstIn(file.getName).nonEmpty)

  def otherJars: List[File] = files.filterNot(_.getName.contains("scala-compiler"))

  def hydraBridge: Option[File] = files.find(file => HydraBridgeRegex.findFirstIn(file.getName).nonEmpty)
}

object HydraData {
  val HydraBridgeName = "hydra-bridge_1_0"
  val HydraBridgeNameRegex = s".*${HydraData.HydraBridgeName}-(\\d+\\.\\d+\\.\\d+)-sources.jar".r

  def apply(project: JpsProject, scalaVersion: String): HydraData = {
    val hydraProjectSettings = SettingsManager.getHydraSettings(project)
    val files = SettingsManager.getGlobalHydraSettings(project.getModel.getGlobal).getArtifactsFor(scalaVersion,hydraProjectSettings.getHydraVersion).asScala.map(new File(_)).toList

    new HydraData(project, files, scalaVersion)
  }

  def getHydraVersionFromBridge(bridgeFile: File): Option[String] = {
    val fileName = bridgeFile.getName
    fileName match {
      case HydraBridgeNameRegex(hydraVersion) => Some(hydraVersion)
      case _ => None
    }
  }
}