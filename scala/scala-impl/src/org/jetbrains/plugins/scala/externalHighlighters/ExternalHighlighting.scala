package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.lang.annotation.HighlightSeverity

/**
 * All information that needed for highlighting.
 *
 * Note: potentially this class can be a sealed ADT.
 */
case class ExternalHighlighting(severity: HighlightSeverity,
                                message: String,
                                line: Int,
                                column: Int)
