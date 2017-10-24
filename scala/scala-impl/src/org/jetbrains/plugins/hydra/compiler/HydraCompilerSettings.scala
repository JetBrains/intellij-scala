package org.jetbrains.plugins.hydra.compiler

import java.io.File

import com.intellij.openapi.components._
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.hydra.compiler.SourcePartitioner.Auto

import scala.beans.BeanProperty

/**
  * @author Maris Alexandru
  */
@State(
        name = "HydraSettings",
        storages = Array(new Storage("hydra.xml"))
)
class HydraCompilerSettings(project: Project) extends PersistentStateComponent[HydraCompilerSettingsState] {

  private val ProjectRoot: String = getProjectRootPath

  var isHydraEnabled: Boolean = false

  var isHydraSettingsEnabled: Boolean = false

  var hydraVersion: String = "0.9.5"

  var noOfCores: String = Math.ceil(Runtime.getRuntime.availableProcessors()/2D).toInt.toString

  var hydraStorePath: String = getDefaultHydraStorePath

  var sourcePartitioner: String = Auto.value

  override def getState: HydraCompilerSettingsState = {
    val state = new HydraCompilerSettingsState()
    state.hydraVersion = hydraVersion
    state.noOfCores = noOfCores
    state.isHydraEnabled = isHydraEnabled
    state.hydraStorePath = hydraStorePath
    state.sourcePartitioner = sourcePartitioner
    state.projectRoot = ProjectRoot
    state.isHydraSettingsEnabled = isHydraSettingsEnabled
    state
  }

  override def loadState(state: HydraCompilerSettingsState): Unit = {
    isHydraEnabled = state.isHydraEnabled
    hydraVersion = state.hydraVersion
    noOfCores = state.noOfCores
    hydraStorePath = state.hydraStorePath
    sourcePartitioner = state.sourcePartitioner
    isHydraSettingsEnabled = state.isHydraSettingsEnabled
  }

  def getDefaultHydraStorePath: String = ProjectRoot + File.separator + ".hydra"

  private def getProjectRootPath: String = project.getBaseDir.getPresentableUrl
}

object HydraCompilerSettings {
  def getInstance(project: Project): HydraCompilerSettings = ServiceManager.getService(project, classOf[HydraCompilerSettings])
}

class HydraCompilerSettingsState {
  @BeanProperty
  var isHydraEnabled: Boolean = false

  @BeanProperty
  var hydraVersion: String = ""

  @BeanProperty
  var noOfCores: String = ""

  @BeanProperty
  var hydraStorePath: String = ""

  @BeanProperty
  var sourcePartitioner: String = ""

  @BeanProperty
  var projectRoot: String = ""

  @BeanProperty
  var isHydraSettingsEnabled: Boolean = false
}

object SourcePartitioner {
  sealed abstract class SourcePartitioner(val value: String)

  case object Auto extends SourcePartitioner("auto")
  case object Explicit extends SourcePartitioner("explicit")
  case object Plain extends SourcePartitioner("plain")
  case object Package extends SourcePartitioner("package")

  val values: Seq[SourcePartitioner] = Seq(Auto, Explicit, Plain, Package)
}
