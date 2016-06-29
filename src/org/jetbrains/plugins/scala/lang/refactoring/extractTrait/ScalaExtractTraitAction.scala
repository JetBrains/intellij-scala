package org.jetbrains.plugins.scala
package lang.refactoring.extractTrait

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.refactoring.actions.ExtractSuperActionBase
import org.jetbrains.plugins.scala.lang.refactoring.ScalaRefactoringSupportProvider

/**
 * Nikolay.Tropin
 * 2014-05-20
 */
class ScalaExtractTraitAction extends ExtractSuperActionBase {

  override def getRefactoringHandler(provider: RefactoringSupportProvider): ScalaExtractTraitHandler = provider match {
    case _: ScalaRefactoringSupportProvider => new ScalaExtractTraitHandler
    case _ => null
  }
}
