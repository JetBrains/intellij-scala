package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.template._
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil

/**
 * @author Roman.Shein
 * @since 24.09.2015.
 */
class ScalaQualifiedClassNameMacro extends ScalaMacro("macro.qualified.class.name") {

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    Option(PsiTreeUtil.getParentOfType(context.getPsiElementAtStartOffset, classOf[PsiClass])).map(_.getQualifiedName).
            map(new TextResult(_)).orNull
  }

  override def getDefaultValue: String = ScalaMacro.DefaultValue
}
