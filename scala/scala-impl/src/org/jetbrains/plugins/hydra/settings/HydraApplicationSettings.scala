package org.jetbrains.plugins.hydra.settings

import java.io.File
import java.net.URL
import java.util

import com.intellij.openapi.components._

import scala.beans.BeanProperty
import scala.collection.JavaConverters._

/**
  * @author Maris Alexandru
  */
@State(
  name = "HydraApplicationSettings",
  storages = Array(new Storage("hydra_config.xml"))
)
class HydraApplicationSettings extends PersistentStateComponent[HydraApplicationSettingsState]{

  var artifactPaths: Map[(String, String), List[String]] = Map.empty
  var hydraRepositoryUrl: String = HydraApplicationSettings.DefaultHydraRepositoryUrl
  var hydraRepositoryRealm: String = HydraApplicationSettings.DefaultHydraRepositoryRealm
  private val KeySeparator = "_"

  override def loadState(state: HydraApplicationSettingsState): Unit = {
    state.removeMapEntriesThatDontExist()
    artifactPaths = convertArtifactsFromStateToSettings(state.getGlobalArtifactPaths)
    hydraRepositoryUrl = state.getHydraRepositoryUrl
  }

  override def getState: HydraApplicationSettingsState = {
    val state = new HydraApplicationSettingsState
    val artifacts = artifactPaths map { case((scalaVer, hydraVer), value) => (scalaVer + KeySeparator + hydraVer, value.asJava)}
    state.setGlobalArtifactPaths(artifacts.asJava)
    state.removeMapEntriesThatDontExist()
    state.setHydraRepositoryUrl(hydraRepositoryUrl)
    artifactPaths = convertArtifactsFromStateToSettings(state.getGlobalArtifactPaths)
    state
  }

  def getDownloadedHydraVersions: Array[String] = {
    for { (_, hydraVersion) <- artifactPaths.keySet.toArray } yield hydraVersion
  }

  def getDownloadedScalaVersions: Array[String] = {
    for { (scalaVersion, _) <- artifactPaths.keySet.toArray } yield scalaVersion
  }

  def getHydraRepositoryName: String = new URL(HydraApplicationSettings.getInstance().hydraRepositoryUrl).getHost

  def getHydraRepositoryUrl: String = if(hydraRepositoryUrl.endsWith("/")) hydraRepositoryUrl.dropRight(1) else hydraRepositoryUrl

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

  @BeanProperty
  var hydraRepositoryUrl: String = HydraApplicationSettings.DefaultHydraRepositoryUrl

  @BeanProperty
  var hydraRepositoryRealm: String = HydraApplicationSettings.DefaultHydraRepositoryRealm

  def removeMapEntriesThatDontExist(): Unit = {
    globalArtifactPaths = globalArtifactPaths.asScala.filter(entry => checkIfArtifactsExist(entry._2)).asJava
  }

  def checkIfArtifactsExist(artifacts: util.List[String]): Boolean = {
    artifacts.asScala.forall(new File(_).exists())
  }
}

object HydraApplicationSettings {
  val DefaultHydraRepositoryName = "repo.triplequote.com"
  val DefaultHydraRepositoryUrl = s"https://$DefaultHydraRepositoryName/artifactory"
  val DefaultHydraRepositoryRealm = "Artifactory Realm"
  def getInstance(): HydraApplicationSettings = ServiceManager.getService(classOf[HydraApplicationSettings])
}