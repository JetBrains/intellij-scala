package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.template._
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil

/**
  * @author Roman.Shein
  *         Date: 21.12.2015
  */
class ScalaPrimaryConstructorParamsMacro extends ScalaMacro("macro.primaryConstructor.param.instances") {

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    MacroUtil.getPrimaryConbstructorParams(context).map(new PsiElementResult(_)).orNull
  }

  override protected def message(nameKey: String): String = ScalaMacro.message(nameKey)
}
