package org.jetbrains.plugins.scala.project.settings

import org.jetbrains.plugins.scala.compiler.data.{CompileOrder, DebuggingInfoLevel, ScalaCompilerSettingsState, ScalaCompilerSettingsStateBuilder}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

/**
 * This class represents scala compiler settings which are supposed to be used
 * in IntelliJ IDEA code analyses features via [[org.jetbrains.plugins.scala.project.ModuleExt.scalaCompilerSettings]]
 *
 * There are some other classes related to compiler settings, which serve different purposes:
 *  - [[org.jetbrains.plugins.scala.compiler.data.ScalaCompilerSettingsState]]
 *  - [[org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfigurable]]
 *  - `org.jetbrains.jps.incremental.scala.model.CompilerSettingsImpl`
 */
case class ScalaCompilerSettings(compileOrder: CompileOrder,
                                 nameHashing: Boolean,
                                 recompileOnMacroDef: Boolean,
                                 transitiveStep: Int,
                                 recompileAllFraction: Double,

                                 //language features start (NOTE: we could extract them to a separate class)
                                 dynamics: Boolean,
                                 postfixOps: Boolean,
                                 reflectiveCalls: Boolean,
                                 implicitConversions: Boolean,
                                 higherKinds: Boolean,
                                 existentials: Boolean,
                                 macros: Boolean,
                                 //language features end

                                 experimental: Boolean,
                                 warnings: Boolean,
                                 deprecationWarnings: Boolean,
                                 uncheckedWarnings: Boolean,
                                 featureWarnings: Boolean,
                                 optimiseBytecode: Boolean,
                                 explainTypeErrors: Boolean,
                                 specialization: Boolean,
                                 continuations: Boolean,
                                 debuggingInfoLevel: DebuggingInfoLevel,
                                 // single field for dotty & scala3 artifacts,
                                 // assuming only one can be used in a module at a time
                                 additionalCompilerOptions: Seq[String],
                                 plugins: Seq[String]) {

  //Fields defined here exist only as performance optimisation.
  //They are supposed to be frequently used during the code analyses.
  //For these settings we don't have separate setting on UI in the compiler profile settings
  //TODO: analyze other places which can call `additionalCompilerOptions` frequently and rewrite them as well to use cached value
  val languageWildcard: Boolean = additionalCompilerOptions.contains("-language:_") ||
    additionalCompilerOptions.contains("--language:_")
  val strict: Boolean = additionalCompilerOptions.contains("-strict")
  val strictEquality: Boolean = additionalCompilerOptions.contains("-language:strictEquality") ||
    additionalCompilerOptions.contains("--language:strictEquality")

  def getOptionsAsStrings(forScala3Compiler: Boolean): Seq[String] = {
    val state = toState
    ScalaCompilerSettingsStateBuilder.getOptionsAsStrings(state, forScala3Compiler, canonisePath = false)
  }

  def toState: ScalaCompilerSettingsState = {
    val state = new ScalaCompilerSettingsState()
    state.compileOrder = compileOrder
    state.nameHashing = nameHashing
    state.recompileOnMacroDef = recompileOnMacroDef
    state.transitiveStep = transitiveStep
    state.recompileAllFraction = recompileAllFraction
    state.dynamics = dynamics
    state.postfixOps = postfixOps
    state.reflectiveCalls = reflectiveCalls
    state.implicitConversions = implicitConversions
    state.higherKinds = higherKinds
    state.existentials = existentials
    state.macros = macros
    state.experimental = experimental
    state.warnings = warnings
    state.deprecationWarnings = deprecationWarnings
    state.uncheckedWarnings = uncheckedWarnings
    state.featureWarnings = featureWarnings
    state.optimiseBytecode = optimiseBytecode
    state.explainTypeErrors = explainTypeErrors
    state.specialization = specialization
    state.continuations = continuations
    state.debuggingInfoLevel = debuggingInfoLevel
    state.additionalCompilerOptions = additionalCompilerOptions.toArray
    state.plugins = plugins.toArray
    state
  }
}

object ScalaCompilerSettings {
  def scalaVersionSinceWhichHigherKindsAreAlwaysEnabled: ScalaVersion =
    LatestScalaVersions.Scala_2_13.withMinor(1)

  def fromState(state: ScalaCompilerSettingsState): ScalaCompilerSettings =
    ScalaCompilerSettings(
      compileOrder = state.compileOrder,
      nameHashing = state.nameHashing,
      recompileOnMacroDef = state.recompileOnMacroDef,
      transitiveStep = state.transitiveStep,
      recompileAllFraction = state.recompileAllFraction,

      dynamics = state.dynamics,
      postfixOps = state.postfixOps,
      reflectiveCalls = state.reflectiveCalls,
      implicitConversions = state.implicitConversions,
      higherKinds = state.higherKinds,
      existentials = state.existentials,
      macros = state.macros,

      experimental = state.experimental,
      warnings = state.warnings,
      deprecationWarnings = state.deprecationWarnings,
      uncheckedWarnings = state.uncheckedWarnings,
      featureWarnings = state.featureWarnings,
      optimiseBytecode = state.optimiseBytecode,
      explainTypeErrors = state.explainTypeErrors,
      specialization = state.specialization,
      continuations = state.continuations,
      debuggingInfoLevel = state.debuggingInfoLevel,
      additionalCompilerOptions = state.additionalCompilerOptions.toSeq,
      plugins = state.plugins.toSeq
    )

  def fromOptions(options: Seq[String], compileOrder: CompileOrder): ScalaCompilerSettings = {
    val state = ScalaCompilerSettingsStateBuilder.stateFromOptions(options, compileOrder)
    fromState(state)
  }
}
