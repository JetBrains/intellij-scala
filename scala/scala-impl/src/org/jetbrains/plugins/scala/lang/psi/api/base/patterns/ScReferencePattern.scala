package org.jetbrains.plugins.scala.lang.psi.api.base
package patterns

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScBegin
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariableDefinition

trait ScReferencePattern extends ScBindingPattern {
  override def setName(name: String): PsiElement = {
    this.parentsInFile
      .findByType[ScValueOrVariableDefinition with ScBegin]
      .filter(valOrVal => valOrVal.tag == this && valOrVal.isSimple)
      .flatMap(_.end)
      .foreach(_.setName(name))
    super.setName(name)
  }
}
