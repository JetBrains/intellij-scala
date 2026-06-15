package org.jetbrains.sbt.process.options.parsing.model

/**
 * Source of a raw sbt option batch, used for diagnostics.
 */
private[options] enum SbtOptionsSource {
  /** From IDE settings */
  case IdeSettings
  /** From .sbtopts file loaded by the sbt launcher script */
  case OptionsFile
  /** From `SBT_OPTS` environment variable */
  case EnvironmentVariable
}
