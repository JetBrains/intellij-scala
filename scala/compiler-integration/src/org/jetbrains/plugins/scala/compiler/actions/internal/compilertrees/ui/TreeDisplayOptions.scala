package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ui

/**
 * Encapsulates visibility options for the compiler trees dialog.
 *
 * @param showEmptyPhases whether to show phases with empty tree content
 * @param showTasty whether to show Tasty output phases
 * @param showUncapturedMessages whether to show uncaptured compiler output (warnings, errors, etc.)
 */
case class TreeDisplayOptions(
  showEmptyPhases: Boolean,
  showTasty: Boolean,
  showUncapturedMessages: Boolean
)

object TreeDisplayOptions {
  /** Default display options: hide empty phases, show everything else */
  val Default: TreeDisplayOptions = TreeDisplayOptions(
    showEmptyPhases = false,
    showTasty = false,
    showUncapturedMessages = true
  )
}
