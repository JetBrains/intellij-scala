package org.jetbrains.plugins.scala.lang.completion.command

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.plugins.scala.codeInsight.intention.CreateCompanionObjectIntention

import java.util

final class CreateCompanionObjectCommandProvider extends ScalaIntentionOnIdentifierCommandProvider {
  override def getIntention: IntentionAction = new CreateCompanionObjectIntention

  override def getSynonyms: util.List[String] = util.List.of("Create companion object")
}
