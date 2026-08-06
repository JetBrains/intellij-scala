package org.jetbrains.plugins.scala.project

import java.nio.file.Path

/**
 * Represents a classpath required for running the Scala REPL.
 * Historically, this classpath was a compile dependency of the `scala-compiler` and `scala3-compiler_3` artifacts.
 * As of Scala 3.8, it is no longer bundled with the Scala compiler and needs to be resolved separately.
 */
sealed trait ReplClasspath {
  def asPaths: Seq[Path] = this match {
    case ReplClasspath.Bundled => Seq.empty
    case ReplClasspath.Provided(classpath) => classpath
  }
}

object ReplClasspath {
  /**
   * Represents the REPL classpath bundled as a part of the Scala compiler classpath.
   */
  case object Bundled extends ReplClasspath

  /**
   * Represents a separate REPL classpath which needs to be included at runtime to execute the Scala REPL.
   */
  final case class Provided(classpath: Seq[Path]) extends ReplClasspath

  def fromPaths(paths: Seq[Path]): ReplClasspath =
    if (paths.isEmpty) Bundled else Provided(paths)
}
