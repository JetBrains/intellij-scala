package org.jetbrains.plugins.scala
package lang
package parameterInfo
package typeParameterInfo

import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithTabActionSupport
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeArgs

/**
  * @author Aleksander Podkhalyuzin
  * @since 26.04.2009
  */
abstract class TypeParameterInfoTestBase extends ParameterInfoTestBase[ScTypeArgs] {

  override protected def folderPath: String = super.folderPath + "typeParameterInfo/"

  override protected def createHandler: ParameterInfoHandlerWithTabActionSupport[ScTypeArgs, Any, _ <: PsiElement] =
    new ScalaTypeParameterInfoHandler
}