package org.jetbrains.plugins.scala.compiler.highlighting.compilation

import com.intellij.openapi.vfs.VirtualFile

/** What has to be done to obtain up-to-date diagnostics for a file. */
enum CompilationDecision {

  /**
   * The file's build target is current and nothing anywhere is unbuilt, so its stored highlightings hold and
   * no compilation is needed.
   */
  case UpToDate

  /**
   * The last compilation compiled this file and nothing has changed since, so the diagnostics recorded for
   * it still describe it and no compilation is needed.
   *
   * Unlike [[UpToDate]] this says nothing about the file's target: the compilation that covered the file may
   * well have failed, and usually did. It is the answer for a file in a module that does not build, whose
   * own state is known.
   */
  case Recorded

  /**
   * This file is the only thing not in its target's output, and that output was built against the current
   * state of everything the target depends on, so the document compiler can check the file against it.
   *
   * Both halves matter. Being the only unbuilt file is not enough on its own: a target can be behind because
   * something it depends on was rebuilt and it was not.
   */
  case Document

  /**
   * Something else is unbuilt, or the target was never built, or its output is behind a dependency's.
   *
   * `scope` is every unbuilt file plus the file this decision was asked about. 
   */
  case Incremental(scope: Set[VirtualFile])

  /**
   * Whether the output of the file's build target can be relied upon: false only for [[Incremental]] and [[Recorded]],
   * the decisions that say the output is missing or behind.
   */
  def trustsBuildOutput: Boolean = this match {
    case Incremental(_) | Recorded => false
    case _ => true
  }

  override def toString: String = this match {
    case UpToDate => "up to date"
    case Recorded => "recorded"
    case Document => "document"
    case Incremental(scope) => s"incremental (${scope.map(_.getPresentableName).mkString(",")} file(s))"
  }
}
