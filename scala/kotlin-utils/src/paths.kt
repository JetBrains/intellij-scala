package org.jetbrains.plugins.scala.kotlin.util

import java.nio.file.Path
import kotlin.io.path.relativeTo as stdRelativeTo

/**
 * Convenience method to be called from Scala.
 *
 * Calculates the relative path for this path from a [base] path.
 *
 * Note that the [base] path is treated as a directory.
 * If this path matches the [base] path, then a [Path] with an empty path will be returned.
 *
 * @return the relative path from [base] to this.
 *
 * @throws IllegalArgumentException if this and base paths have different roots.
 *
 * @see kotlin.io.path.relativeTo
 */
fun Path.relativeTo(base: Path): Path = this.stdRelativeTo(base)
