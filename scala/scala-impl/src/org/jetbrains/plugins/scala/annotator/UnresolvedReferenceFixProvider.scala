package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.plugins.scala.ExtensionPointDeclaration
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement

/**
  * @author Pavel Fatin
  */
abstract class UnresolvedReferenceFixProvider {
  def fixesFor(reference: ScReferenceElement): Seq[IntentionAction]
}

object UnresolvedReferenceFixProvider
  extends ExtensionPointDeclaration[UnresolvedReferenceFixProvider]("org.intellij.scala.unresolvedReferenceFixProvider")
