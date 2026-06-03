package org.jetbrains.plugins.scala
package lang
package parameterInfo
package typeParameterInfo

import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithTabActionSupport
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeArgs

abstract class TypeParameterInfoTestBase extends ParameterInfoTestBase[ScTypeArgs] {

  override def getTestDataPath: String =
    s"${super.getTestDataPath}typeParameterInfo/"

  override protected def createHandler: ParameterInfoHandlerWithTabActionSupport[ScTypeArgs, Any, _ <: PsiElement] =
    new ScalaTypeParameterInfoHandler
}