package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement

/**
  * @author Pavel Fatin
  */
abstract class UnresolvedReferenceFixProvider {
  def fixesFor(reference: ScReferenceElement): Seq[IntentionAction]
}

object UnresolvedReferenceFixProvider {
  var EP_NAME: ExtensionPointName[UnresolvedReferenceFixProvider] =
    ExtensionPointName.create("org.intellij.scala.unresolvedReferenceFixProvider")
}
