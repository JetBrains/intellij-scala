package org.jetbrains.plugins.scala
package lang
package parameterInfo
package functionParameterInfo

import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithTabActionSupport
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
  * User: Alexander Podkhalyuzin
  * Date: 02.03.2009
  */
abstract class FunctionParameterInfoTestBase extends ParameterInfoTestBase[PsiElement, ScExpression] {

  override protected def folderPath: String = super.folderPath + "functionParameterInfo/"

  override protected def createHandler: ParameterInfoHandlerWithTabActionSupport[PsiElement, Any, ScExpression] =
    new ScalaFunctionParameterInfoHandler
}
