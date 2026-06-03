package org.jetbrains.plugins.scala.util

import com.intellij.testFramework.EditorTestUtil

trait ShortCaretMarker {
  val | : String = EditorTestUtil.CARET_TAG
}

object ShortCaretMarker extends ShortCaretMarker
