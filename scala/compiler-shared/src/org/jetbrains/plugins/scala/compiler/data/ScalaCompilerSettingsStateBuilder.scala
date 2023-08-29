package org.jetbrains.plugins.scala.compiler.data

import com.intellij.openapi.util.io.FileUtil

/**
 * @see [[ScalaCompilerSettingsState]]
 */
object ScalaCompilerSettingsStateBuilder {

  private val PluginOptionPattern = "-Xplugin:(.+)".r

  private val DebuggingOptions: Map[String, DebuggingInfoLevel] = Map(
    "-g:none" -> DebuggingInfoLevel.None,
    "-g:source" -> DebuggingInfoLevel.Source,
    "-g:line" -> DebuggingInfoLevel.Line,
    "-g:vars" -> DebuggingInfoLevel.Vars,
    "-g:notailcalls" -> DebuggingInfoLevel.Notailcalls
  )

  private val DebuggingInfoLevelToScalacOption: Map[DebuggingInfoLevel, String] =
    DebuggingOptions.map(_.swap)

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
    ("-optimise", _.optimiseBytecode, _.optimiseBytecode = _),
    ("-explaintypes", _.explainTypeErrors, _.explainTypeErrors = _),
    ("-no-specialization", !_.specialization, (s, x) => s.specialization = !x),
    ("-P:continuations:enable", _.continuations, _.continuations = _)
  )

  def getOptionsAsStrings(
    state: ScalaCompilerSettingsState,
    forScala3Compiler: Boolean,
    canonisePath: Boolean
  ): Seq[String] = {
    val toggledOptions: Seq[String] =
      ToggleOptions.collect {
        case (option, getter, _) if getter(state) =>
          option
      }

    //TODO: SCL-16881 Support "Debugging info level" for dotty
    val debuggingLevelOption: Option[String] =
      if (!forScala3Compiler) DebuggingInfoLevelToScalacOption.get(state.debuggingInfoLevel)
      else None

    val pluginOptions: Array[String] = state.plugins.map(path => "-Xplugin:" + (if (canonisePath) FileUtil.toCanonicalPath(path) else path))

    toggledOptions ++ debuggingLevelOption ++ pluginOptions ++ state.additionalCompilerOptions
  }

  def stateFromOptions(options: Seq[String], compileOrder: CompileOrder): ScalaCompilerSettingsState = {
    val state = new ScalaCompilerSettingsState
    fillStateFromOptions(state, options, compileOrder)
    state
  }

  private def fillStateFromOptions(
    state: ScalaCompilerSettingsState,
    options: Seq[String],
    compileOrder: CompileOrder
  ): Unit = {
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

    state.compileOrder = compileOrder
  }

  private def normalized(options: Seq[String]): Seq[String] = options.iterator.flatMap { option =>
    if (option == "-language:macros")
      Seq("-language:experimental.macros")
    else if (option.startsWith("-language:"))
      option.substring(10).split(",").map("-language:" + _)
    else if (option.startsWith("-Xplugin:"))
      option.substring(9).split(";").map("-Xplugin:" + _)
    else
      Seq(option)
  }.toSeq
}
