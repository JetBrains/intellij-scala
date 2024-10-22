package org.jetbrains.plugins.scala.lang.completion

import com.intellij.lang.DefaultWordCompletionFilter

final class ScalaWordCompletionFilter extends DefaultWordCompletionFilter {
  override def isWordCompletionInDumbModeEnabled: Boolean = false
}
