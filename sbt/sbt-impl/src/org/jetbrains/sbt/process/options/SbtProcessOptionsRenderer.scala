package org.jetbrains.sbt.process.options

import org.jetbrains.sbt.process.options.collecting.SbtOptionsCollector
import org.jetbrains.sbt.process.options.knownOptions.KnownSbtOptionArgMapping.MappedArguments
import org.jetbrains.sbt.process.options.parsing.model.ParsedSbtOption

/**
 * Renders already-collected and parsed sbt options into process argument buckets.
 *
 * Collection, source attribution, and warning reporting live in [[SbtProcessOptionsResolver]].
 *
 * Coverage:
 * - Indirectly covered by [[org.jetbrains.sbt.process.options.SbtProcessOptionsResolverTest]] through separate-process
 *   and shell resolution.
 */
private[options] object SbtProcessOptionsRenderer {

  private[options] def renderForSeparateProcess(options: SbtOptionsCollector.CollectionResult, projectPath: String): SbtProcessOptions =
    renderForTarget(options, projectPath, args => args.vmOptions)

  private[options] def renderForShell(options: SbtOptionsCollector.CollectionResult, projectPath: String): SbtProcessOptions =
    renderForTarget(options, projectPath, args => args.vmOptions ++ args.vmOptionsShellOnly)

  private def renderForTarget(
    options: SbtOptionsCollector.CollectionResult,
    projectPath: String,
    vmOptions: MappedArguments => Seq[String]
  ): SbtProcessOptions = {
    val argumentsByOption = options.parsed.map(renderOption(_, projectPath, vmOptions))

    SbtProcessOptions(
      argumentsByOption.flatMap(_.allVmOptions),
      argumentsByOption.flatMap(_.sbtLauncherArgs)
    )
  }

  private def renderOption(
    option: ParsedSbtOption,
    projectPath: String,
    vmOptions: MappedArguments => Seq[String]
  ): SbtProcessOptions =
    option match {
      case ParsedSbtOption.RawJvmSbtOption(value) =>
        SbtProcessOptions(Seq(value), Seq.empty)

      case ParsedSbtOption.DefinedSbtOption(entry, parsedValue) =>
        val arguments = entry.argMapping.toArguments(parsedValue, projectPath)
        SbtProcessOptions(vmOptions(arguments), arguments.launcherArgs)
    }
}
