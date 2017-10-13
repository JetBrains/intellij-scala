package org.jetbrains.plugins.scala.settings

import java.io.File
import java.util
import scala.collection.JavaConverters._
import com.intellij.openapi.components._

import scala.beans.BeanProperty

/**
  * @author Maris Alexandru
  */
@State(
  name = "HydraApplicationSettings",
  storages = Array(new Storage("hydra_config.xml"))
)
class HydraApplicationSettings extends PersistentStateComponent[HydraApplicationSettingsState]{

  var artifactPaths: Map[(String, String), List[String]] = Map.empty
  private val KeySeparator = "_"

  override def loadState(state: HydraApplicationSettingsState): Unit = {
    state.removeMapEntriesThatDontExist()
    artifactPaths = convertArtifactsFromStateToSettings(state.getGlobalArtifactPaths)
  }

  override def getState: HydraApplicationSettingsState = {
    val state = new HydraApplicationSettingsState
    val artifacts = artifactPaths map { case((scalaVer, hydraVer), value) => (scalaVer + KeySeparator + hydraVer, value.asJava)}
    state.setGlobalArtifactPaths(artifacts.asJava)
    state.removeMapEntriesThatDontExist()
    artifactPaths = convertArtifactsFromStateToSettings(state.getGlobalArtifactPaths)
    state
  }

  private def convertArtifactsFromStateToSettings(artifacts: java.util.Map[String, java.util.List[String]]) = {
    val resultArtifacts = for {
      (key, value) <- artifacts.asScala
      scalaVersion <- key.split(KeySeparator).headOption
      hydraVersion <- key.split(KeySeparator).tail.headOption
    } yield ((scalaVersion, hydraVersion), value.asScala.toList)

    resultArtifacts.toMap
  }
}

class HydraApplicationSettingsState {
  @BeanProperty
  var globalArtifactPaths: java.util.Map[String, java.util.List[String]] = new java.util.HashMap()

  def removeMapEntriesThatDontExist(): Unit = {
    globalArtifactPaths = globalArtifactPaths.asScala.filter(entry => checkIfArtifactsExist(entry._2)).asJava
  }

  def checkIfArtifactsExist(artifacts: util.List[String]): Boolean = {
    artifacts.asScala.forall(new File(_).exists())
  }
}

object HydraApplicationSettings {
  def getInstance(): HydraApplicationSettings = ServiceManager.getService(classOf[HydraApplicationSettings])
}