package org.jetbrains.bsp.project

import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.build.CompilerEventReporter

/**
 * Used for passing custom arguments to `BspTask` when running compiler based highlighting.
 */
private[jetbrains] case class CustomTaskArguments(@Nls message: String, reporter: CompilerEventReporter)
