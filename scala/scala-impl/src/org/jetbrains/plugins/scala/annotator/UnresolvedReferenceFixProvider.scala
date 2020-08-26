package org.jetbrains.plugins.scala
package annotator

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

abstract class UnresolvedReferenceFixProvider {
  def fixesFor(reference: ScReference): Seq[IntentionAction]
}

object UnresolvedReferenceFixProvider
  extends ExtensionPointDeclaration[UnresolvedReferenceFixProvider]("org.intellij.scala.unresolvedReferenceFixProvider") {

  def fixesFor(reference: ScReference): collection.Seq[IntentionAction] =
    implementations.flatMap {
      _.fixesFor(reference)
    }
}
