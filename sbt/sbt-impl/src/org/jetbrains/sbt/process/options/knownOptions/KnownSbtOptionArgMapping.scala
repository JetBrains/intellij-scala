package org.jetbrains.sbt.process.options.knownOptions

/**
 * Converts one known sbt option occurrence into renderer argument buckets
 *
 * @see [[SbtProcessOptionsRenderer]] for bucket semantics
 */
private[options] trait KnownSbtOptionArgMapping {
  def toArguments(parsedValue: Option[String], projectPath: String): KnownSbtOptionArgMapping.MappedArguments
}

/**
 * Argument buckets produced by known sbt option mappings.
 */
private[options] object KnownSbtOptionArgMapping {

  /**
   * Argument buckets contributed by one parsed sbt option.
   *
   * @param vmOptions          JVM options used by both shell and non-shell sbt process launches
   * @param vmOptionsShellOnly JVM options added only to the interactive sbt shell process
   * @param launcherArgs       program arguments passed to the sbt launcher
   */
  final case class MappedArguments(
    vmOptions: Seq[String] = Seq.empty,
    vmOptionsShellOnly: Seq[String] = Seq.empty,
    launcherArgs: Seq[String] = Seq.empty
  )

  /**
   * Convenience constructors for common option argument targets.
   */
  object MappedArguments {
    /** Renders the same JVM options for shell and non-shell sbt launches. */
    def allJvm(vmOptions: Seq[String]): MappedArguments =
      MappedArguments(vmOptions = vmOptions)

    /** Renders JVM options only for the interactive sbt shell launch. */
    def shellJvm(vmOptions: Seq[String]): MappedArguments =
      MappedArguments(vmOptionsShellOnly = vmOptions)

    /** Renders arguments passed directly to the sbt launcher. */
    def launcher(args: Seq[String]): MappedArguments =
      MappedArguments(launcherArgs = args)
  }
}
