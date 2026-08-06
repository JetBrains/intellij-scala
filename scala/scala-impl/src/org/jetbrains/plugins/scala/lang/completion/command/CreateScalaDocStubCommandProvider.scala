package org.jetbrains.plugins.scala.lang.completion.command

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.plugins.scala.editor.documentationProvider.actions.CreateScalaDocStubIntentionAction

import java.util

final class CreateScalaDocStubCommandProvider extends ScalaIntentionOnIdentifierCommandProvider {
  override def getPriority: Int = -200

  override def getIntention: IntentionAction = new CreateScalaDocStubIntentionAction

  override def getSynonyms: util.List[String] = util.List.of("Add ScalaDoc")
}
