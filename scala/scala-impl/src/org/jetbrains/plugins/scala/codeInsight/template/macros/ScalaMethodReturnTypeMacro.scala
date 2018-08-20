package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.template._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.project.ProjectContext

/**
 * @author Roman.Shein
 * @since 24.09.2015.
 */
class ScalaMethodReturnTypeMacro extends ScalaMacro("macro.method.returnType") {

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    implicit val project: ProjectContext = context.getProject
    
    Option(PsiTreeUtil.getParentOfType(context.getPsiElementAtStartOffset, classOf[ScFunction])).
      map(_.`type`().getOrAny match {
              case FunctionType(rt, _) => rt
              case t => t
            }).map(new ScalaTypeResult(_)).orNull
  }

  override def getDefaultValue: String = ScalaMacro.DefaultValue

  override protected def message(nameKey: String): String = ScalaMacro.message(nameKey)
}
