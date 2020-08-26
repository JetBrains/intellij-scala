package org.jetbrains.plugins.scala.project.settings

import org.jetbrains.plugins.scala.compiler.data.SbtIncrementalOptions
import org.jetbrains.plugins.scala.project.{CompileOrder, DebuggingInfoLevel}

/**
 * @author Pavel Fatin
 */
case class ScalaCompilerSettings(compileOrder: CompileOrder,
                                 nameHashing: Boolean,
                                 recompileOnMacroDef: Boolean,
                                 transitiveStep: Int,
                                 recompileAllFraction: Double,
                                 dynamics: Boolean,
                                 postfixOps: Boolean,
                                 reflectiveCalls: Boolean,
                                 implicitConversions: Boolean,
                                 higherKinds: Boolean,
                                 existentials: Boolean,
                                 macros: Boolean,
                                 experimental: Boolean,
                                 warnings: Boolean,
                                 deprecationWarnings: Boolean,
                                 uncheckedWarnings: Boolean,
                                 featureWarnings: Boolean,
                                 strict: Boolean, // Scala 3 flag to enforce 3.1 features in 3.0
                                 optimiseBytecode: Boolean,
                                 explainTypeErrors: Boolean,
                                 specialization: Boolean,
                                 continuations: Boolean,
                                 debuggingInfoLevel: DebuggingInfoLevel,
                                 dottySbtBridgePath: String,
                                 additionalCompilerOptions: Seq[String],
                                 plugins: Seq[String]) {

  import ScalaCompilerSettings.{DebuggingOptions, ToggleOptions}

  def sbtIncOptions: SbtIncrementalOptions =
    SbtIncrementalOptions(nameHashing, recompileOnMacroDef, transitiveStep, recompileAllFraction)

  def toOptions: Seq[String] = {
    val debuggingLevelToOption = DebuggingOptions.map(_.swap)

    val state = toState
    val toggledOptions = ToggleOptions.collect {
      case (option, getter, _) if getter(state) => option
    }

    val debuggingLevelOption = debuggingLevelToOption(debuggingInfoLevel)

    val pluginOptions = plugins.map(path => "-Xplugin:" + path)

    (toggledOptions :+ debuggingLevelOption) ++ pluginOptions ++ additionalCompilerOptions
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
    state.strict = strict
    state.optimiseBytecode = optimiseBytecode
    state.explainTypeErrors = explainTypeErrors
    state.specialization = specialization
    state.continuations = continuations
    state.debuggingInfoLevel = debuggingInfoLevel
    state.dottySbtBridgePath = dottySbtBridgePath
    state.additionalCompilerOptions = additionalCompilerOptions.toArray
    state.plugins = plugins.toArray
    state
  }
}

object ScalaCompilerSettings {

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
      strict = state.strict,
      optimiseBytecode = state.optimiseBytecode,
      explainTypeErrors = state.explainTypeErrors,
      specialization = state.specialization,
      continuations = state.continuations,
      debuggingInfoLevel = state.debuggingInfoLevel,
      dottySbtBridgePath = state.dottySbtBridgePath,
      additionalCompilerOptions = state.additionalCompilerOptions.toSeq,
      plugins = state.plugins.toSeq
    )

  def fromOptions(options: collection.Seq[String]): ScalaCompilerSettings = {
    val state = new ScalaCompilerSettingsState
    val normalizedOptions = normalized(options)
    val optionToSetter = ToggleOptions.map(it => (it._1, it._3)).toMap

    optionToSetter.foreach {
      case (option, setter) => setter(state, normalizedOptions.contains(option))
    }

    state.debuggingInfoLevel = DebuggingOptions
      .find(p => normalizedOptions.contains(p._1))
      .map(_._2)
      .getOrElse(DebuggingInfoLevel.Vars)

    state.plugins = normalizedOptions.collect {
      case PluginOptionPattern(path) => path
    }.toArray

    state.additionalCompilerOptions = normalizedOptions.filterNot { option =>
      optionToSetter.keySet.contains(option) ||
        DebuggingOptions.keySet.contains(option) ||
        PluginOptionPattern.findFirstIn(option).isDefined
    }.toArray

    fromState(state)
  }

  private def normalized(options: collection.Seq[String]): collection.Seq[String] = options.flatMap {
    case "-language:macros" =>
      Seq("-language:experimental.macros")

    case option if option.startsWith("-language:") =>
      option.substring(10).split(",").map("-language:" + _)

    case option if option.startsWith("-Xplugin:") =>
      option.substring(9).split(";").map("-Xplugin:" + _)

    case option => Seq(option)
  }

  private type BoolGetter = ScalaCompilerSettingsState => Boolean
  private type BoolSetter = (ScalaCompilerSettingsState, Boolean) => Unit

  private val ToggleOptions: Seq[(String, BoolGetter, BoolSetter)] = Seq(
    ("-language:dynamics", _.dynamics, _.dynamics = _),
    ("-language:postfixOps", _.postfixOps, _.postfixOps = _),
    ("-language:reflectiveCalls", _.reflectiveCalls, _.reflectiveCalls = _),
    ("-language:implicitConversions", _.implicitConversions, _.implicitConversions = _),
    ("-language:higherKinds", _.higherKinds, _.higherKinds = _),
    ("-language:existentials", _.existentials, _.existentials = _),
    ("-language:experimental.macros", _.macros, _.macros = _),
    ("-Xexperimental", _.experimental, _.experimental = _),
    ("-nowarn", !_.warnings, (s, x) => s.warnings = !x),
    ("-deprecation", _.deprecationWarnings, _.deprecationWarnings = _),
    ("-unchecked", _.uncheckedWarnings, _.uncheckedWarnings = _),
    ("-feature", _.featureWarnings, _.featureWarnings = _),
    ("-strict", _.strict, _.strict = _),
    ("-optimise", _.optimiseBytecode, _.optimiseBytecode = _),
    ("-explaintypes", _.explainTypeErrors, _.explainTypeErrors = _),
    ("-no-specialization", !_.specialization, (s, x) => s.specialization = !x),
    ("-P:continuations:enable", _.continuations, _.continuations = _))

  private val DebuggingOptions: Map[String, DebuggingInfoLevel] = Map(
    "-g:none" -> DebuggingInfoLevel.None,
    "-g:source" -> DebuggingInfoLevel.Source,
    "-g:line" -> DebuggingInfoLevel.Line,
    "-g:vars" -> DebuggingInfoLevel.Vars,
    "-g:notailcalls" -> DebuggingInfoLevel.Notailcalls
  )

  private val PluginOptionPattern = "-Xplugin:(.+)".r
}
