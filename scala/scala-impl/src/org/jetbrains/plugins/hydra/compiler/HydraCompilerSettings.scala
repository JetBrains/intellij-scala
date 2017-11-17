package org.jetbrains.plugins.hydra.compiler

import java.nio.file.Paths

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

  var hydraVersion: String = ""

  var noOfCores: String = Math.ceil(Runtime.getRuntime.availableProcessors()/2D).toInt.toString

  var hydraStorePath: String = getDefaultHydraStorePath

  var hydraLogLocation: String = Paths.get(getDefaultHydraStorePath, "hydra.log").toString

  var sourcePartitioner: String = Auto.value

  override def getState: HydraCompilerSettingsState = {
    val state = new HydraCompilerSettingsState()
    state.hydraVersion = hydraVersion
    state.noOfCores = noOfCores
    state.isHydraEnabled = isHydraEnabled
    state.hydraStorePath = hydraStorePath
    state.sourcePartitioner = sourcePartitioner
    state.projectRoot = ProjectRoot
    state
  }

  override def loadState(state: HydraCompilerSettingsState): Unit = {
    isHydraEnabled = state.isHydraEnabled
    hydraVersion = state.hydraVersion
    noOfCores = state.noOfCores
    hydraStorePath = state.hydraStorePath
    sourcePartitioner = state.sourcePartitioner
  }

  def getDefaultHydraStorePath: String = Paths.get(ProjectRoot, ".hydra", "idea").toString

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
}

object SourcePartitioner {
  sealed abstract class SourcePartitioner(val value: String)

  case object Auto extends SourcePartitioner("auto")
  case object Explicit extends SourcePartitioner("explicit")
  case object Plain extends SourcePartitioner("plain")
  case object Package extends SourcePartitioner("package")

  val values: Seq[SourcePartitioner] = Seq(Auto, Explicit, Plain, Package)
}
