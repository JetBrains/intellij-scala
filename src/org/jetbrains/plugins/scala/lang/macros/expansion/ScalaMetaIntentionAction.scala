package org.jetbrains.plugins.scala.lang.macros.expansion

import com.intellij.codeInsight.intention.IntentionAction

abstract class ScalaMetaIntentionAction extends IntentionAction {
  override def getFamilyName: String = "Scala.meta"

  override def startInWriteAction(): Boolean = true
}
