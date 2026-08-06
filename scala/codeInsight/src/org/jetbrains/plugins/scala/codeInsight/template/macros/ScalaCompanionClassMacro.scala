package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template.{Expression, ExpressionContext, PsiElementResult, Result}
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.extensions.OptionExt

class ScalaCompanionClassMacro extends ScalaMacro {

  override def getNameShort: String = "companionClass"

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    val clazz = ScalaCompanionClassMacro.companionClass(context.getPsiElementAtStartOffset)
    clazz.map(new PsiElementResult(_)).orNull
  }
}

object ScalaCompanionClassMacro {

  private[macros] def companionClass(element: PsiElement): Option[ScClass] = {
    element
      .parentOfType(classOf[PsiClass])
      .map {
        case scalaObject: ScObject => scalaObject.fakeCompanionClassOrCompanionClass
        case other                 => other
      }
      .filterByType[ScClass]
  }
}
