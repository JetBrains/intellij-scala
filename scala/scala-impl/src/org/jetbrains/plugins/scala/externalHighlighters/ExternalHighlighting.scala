package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.codeInsight.daemon.impl.HighlightInfoType

/**
 * All information that needed for highlighting.
 *
 * Note: potentially this class can be a sealed ADT.
 */
final case class ExternalHighlighting(highlightType: HighlightInfoType,
                                      message: String,
                                      fromLine: Int,
                                      fromColumn: Int,
                                      toLine: Option[Int],
                                      toColumn: Option[Int])