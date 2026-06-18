package org.jetbrains.sbt.shell.process.utils

object SpecialSbtVmOptions {
  /**
   * JVM system property carrying the current IDEA sbt shell run id ([[SbtShellRunId]])
   *
   * Generated sbt settings written by the Scala plugin check it to avoid affecting other sbt sessions.
   * This property is NOT consumed by bundled sbt plugins, such as sbt-structure-extractor or sbt-idea-shell.
   *
   * @see [[org.jetbrains.sbt.project.structure.SbtStructureDumper]] for a similar import guard.
   */
  val IdeaRunIdVmOption = "idea.runid"
}
