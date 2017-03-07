package org.jetbrains.plugins.scala
package lang
package parameterInfo
package functionParameterInfo

import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithTabActionSupport
import com.intellij.psi.PsiElement

/**
  * User: Alexander Podkhalyuzin
  * Date: 02.03.2009
  */
abstract class FunctionParameterInfoTestBase extends ParameterInfoTestBase[PsiElement] {

  override protected def folderPath: String = super.folderPath + "functionParameterInfo/"

  override protected def createHandler: ParameterInfoHandlerWithTabActionSupport[PsiElement, Any, _ <: PsiElement] =
    new ScalaFunctionParameterInfoHandler
}
