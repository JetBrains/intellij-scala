package org.jetbrains.plugins.scala.lang.macros.expansion

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.annotations.NonNls

abstract class ScalaMetaIntentionAction extends IntentionAction {
  @NonNls
  override def getFamilyName: String = "Scala.meta"

  override def startInWriteAction(): Boolean = true
}
