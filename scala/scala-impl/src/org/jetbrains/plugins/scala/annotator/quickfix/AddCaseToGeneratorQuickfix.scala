package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.modcommand.{ActionContext, ModCommand, PsiBasedModCommandAction}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScFor, ScGenerator}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

final class AddCaseToGeneratorQuickfix(gen: ScGenerator) extends PsiBasedModCommandAction[ScGenerator](gen) with DumbAware {
  override def getFamilyName: String = ScalaInspectionBundle.message("add.case")

  override def perform(context: ActionContext, gen: ScGenerator): ModCommand = {
    if (gen.caseKeyword.isDefined) ModCommand.nop()
    else ModCommand.psiUpdate(gen,
      (gen : ScGenerator) => {
        gen.replace(
          ScalaPsiElementFactory.createExpressionFromText(s"for { case ${gen.getText} } ()", gen)(gen)
            .asInstanceOf[ScFor].enumerators.head.generators.head
        )
        ()
      }
    )
  }
}