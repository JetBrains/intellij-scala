package org.jetbrains.plugins.scala
package codeInspection.parameters

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.util.IntentionUtils

/**
 * @author Ksenia.Sautina
 * @since 5/10/12
 */

class NameBooleanParametersQuickFix(element: ScLiteral)
        extends AbstractFixOnPsiElement(ScalaBundle.message("name.boolean.params"), element){

  def doApplyFix(project: Project) {
    val elem = getElement
    if (!elem.isValid) return

    IntentionUtils.addNameToArgumentsFix(elem, onlyBoolean = true) match {
      case Some(x) => x()
      case None =>
    }
  }
}
