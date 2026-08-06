package org.jetbrains.plugins.scala.lang.actions.editor.enter.multiline_string

import com.intellij.lang.Language
import org.jetbrains.plugins.scala.Scala3Language
import org.jetbrains.plugins.scala.lang.actions.editor.enter.AbstractEnterActionTestBase

// Introduced the intermediate trade primarily for the grouping reasons in the class hierarchy view
private[multiline_string]
trait MultiLineStringEnterHandlerTestBase extends AbstractEnterActionTestBase {
  override protected def language: Language = Scala3Language.INSTANCE
}
