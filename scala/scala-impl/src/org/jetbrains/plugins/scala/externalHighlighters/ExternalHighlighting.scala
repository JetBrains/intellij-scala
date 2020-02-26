package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.lang.annotation.HighlightSeverity

/**
 * All information that needed for highlighting.
 *
 * Note: potentially this class can be a sealed ADT.
 */
final case class ExternalHighlighting(severity: HighlightSeverity,
                                      message: String,
                                      fromLine: Int,
                                      fromColumn: Int,
                                      toLine: Option[Int],
                                      toColumn: Option[Int])
