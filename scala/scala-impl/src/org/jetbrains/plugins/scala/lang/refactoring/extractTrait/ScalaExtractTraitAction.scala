package org.jetbrains.plugins.scala
package lang.refactoring.extractTrait

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.refactoring.actions.ExtractSuperActionBase
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.refactoring.ScalaRefactoringSupportProvider

class ScalaExtractTraitAction extends ExtractSuperActionBase {
  locally {
    val presentation = getTemplatePresentation
    presentation.setText(ScalaBundle.message("extract.trait.action.text"))
    presentation.setDescription(ScalaBundle.message("extract.trait.action.description"))
  }

  override def getRefactoringHandler(provider: RefactoringSupportProvider): ScalaExtractTraitHandler = provider match {
    case _: ScalaRefactoringSupportProvider => new ScalaExtractTraitHandler
    case _ => null
  }
}
