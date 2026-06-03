package org.jetbrains.plugins.scala.projectHighlighting.reporter

import com.intellij.openapi.util.TextRange

final case class ErrorDescriptor(range: TextRange, message: String)
