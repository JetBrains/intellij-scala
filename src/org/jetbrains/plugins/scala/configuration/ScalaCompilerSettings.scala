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

  var dynamics: Boolean = _
  var postfixOps: Boolean = _
  var reflectiveCalls: Boolean = _
  var implicitConversions: Boolean = _
  var higherKinds: Boolean = _
  var existentials: Boolean = _
  var macros: Boolean = _

  var warnings: Boolean = _
  var deprecationWarnings: Boolean = _
  var uncheckedWarnings: Boolean = _
  var featureWarnings: Boolean = _
  var optimiseBytecode: Boolean = _
  var explainTypeErrors: Boolean = _
  var continuations: Boolean = _

  var debuggingInfoLevel: DebuggingInfoLevel = _
  var additionalCompilerOptions: String = _
  var plugins: Seq[String] = _

  def parameters: Seq[String] = Seq.empty // TODO

  loadState(new ScalaCompilerSettingsState())

  def loadState(state: ScalaCompilerSettingsState) {
    incrementalityType = state.incrementalityType
    compileOrder = state.compileOrder

    dynamics = state.dynamics
    postfixOps = state.postfixOps
    reflectiveCalls = state.reflectiveCalls
    implicitConversions = state.implicitConversions
    higherKinds = state.higherKinds
    existentials = state.existentials
    macros = state.macros

    warnings = state.warnings
    deprecationWarnings = state.deprecationWarnings
    uncheckedWarnings = state.uncheckedWarnings
    featureWarnings = state.featureWarnings
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

    state.dynamics = dynamics
    state.postfixOps = postfixOps
    state.reflectiveCalls = reflectiveCalls
    state.implicitConversions = implicitConversions
    state.higherKinds = higherKinds
    state.existentials = existentials
    state.macros = macros

    state.warnings = warnings
    state.deprecationWarnings = deprecationWarnings
    state.uncheckedWarnings = uncheckedWarnings
    state.featureWarnings = featureWarnings
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
