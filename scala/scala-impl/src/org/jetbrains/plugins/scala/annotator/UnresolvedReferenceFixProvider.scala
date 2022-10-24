package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.plugins.scala.ExtensionPointDeclaration
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

abstract class UnresolvedReferenceFixProvider {
  def fixesFor(reference: ScReference): Seq[IntentionAction]
}

object UnresolvedReferenceFixProvider
  extends ExtensionPointDeclaration[UnresolvedReferenceFixProvider]("org.intellij.scala.unresolvedReferenceFixProvider") {

  def fixesFor(reference: ScReference): Seq[IntentionAction] =
    implementations.flatMap {
      _.fixesFor(reference)
    }
}
