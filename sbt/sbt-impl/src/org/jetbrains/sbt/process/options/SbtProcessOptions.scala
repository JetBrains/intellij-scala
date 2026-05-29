package org.jetbrains.sbt.process.options

/**
 * Final command-line arguments produced by [[SbtProcessOptionsResolver]].
 *
 * See [[SbtProcessOptionsResolver]] for how JVM options, sbt options, shell-only options, and diagnostics flow into
 * these two buckets.
 *
 * @param allVmOptions    all VM options to pass to the JVM, including VM options derived from sbt options
 * @param sbtLauncherArgs arguments to pass to the sbt launcher
 */
case class SbtProcessOptions(
  allVmOptions: Seq[String],
  sbtLauncherArgs: Seq[String]
)
