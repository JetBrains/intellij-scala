package org.jetbrains.plugins.scala
package configuration

import com.intellij.openapi.components._
import com.intellij.openapi.components.StoragePathMacros._
import com.intellij.openapi.project.Project

/**
 * @author Pavel Fatin
 */
@State(name = "ScalaCompilerConfiguration", storages = Array (
  new Storage(file = PROJECT_FILE),
  new Storage(file = PROJECT_CONFIG_DIR + "/scala_compiler.xml", scheme = StorageScheme.DIRECTORY_BASED)))
class ScalaCompilerSettings extends PersistentStateComponent[ScalaCompilerSettingsState]{
  var incrementalityType: IncrementalityType = _
  var compileOrder: CompileOrder = _
  var warnings: Boolean = _
  var deprecationWarnings: Boolean = _
  var uncheckedWarnings: Boolean = _
  var optimiseBytecode: Boolean = _
  var explainTypeErrors: Boolean = _
  var continuations: Boolean = _
  var debuggingInfoLevel: DebuggingInfoLevel = _
  var additionalCompilerOptions: String = _
  var plugins: Seq[String] = _

  loadState(new ScalaCompilerSettingsState())

  def loadState(state: ScalaCompilerSettingsState) {
    incrementalityType = state.incrementalityType
    compileOrder = state.compileOrder
    warnings = state.warnings
    deprecationWarnings = state.deprecationWarnings
    uncheckedWarnings = state.uncheckedWarnings
    optimiseBytecode = state.optimiseBytecode
    explainTypeErrors = state.explainTypeErrors
    continuations = state.continuations
    debuggingInfoLevel = state.debuggingInfoLevel
    additionalCompilerOptions = state.additionalCompilerOptions
    plugins = state.plugins.toSeq
  }
  
  def getState = {
    val state = new ScalaCompilerSettingsState()
    state.incrementalityType = incrementalityType
    state.compileOrder = compileOrder
    state.warnings = warnings
    state.deprecationWarnings = deprecationWarnings
    state.uncheckedWarnings = uncheckedWarnings
    state.optimiseBytecode = optimiseBytecode
    state.explainTypeErrors = explainTypeErrors
    state.continuations = continuations
    state.debuggingInfoLevel = debuggingInfoLevel
    state.additionalCompilerOptions = additionalCompilerOptions
    state.plugins = plugins.toArray
    state
  }
}

object ScalaCompilerSettings {
  def instanceIn(project: Project): ScalaCompilerSettings =
    ServiceManager.getService(project, classOf[ScalaCompilerSettings])
}
