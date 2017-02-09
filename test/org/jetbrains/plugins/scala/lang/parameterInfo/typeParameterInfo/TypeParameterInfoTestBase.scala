package org.jetbrains.plugins.scala
package lang
package parameterInfo
package typeParameterInfo

import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithTabActionSupport
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTypeArgs, ScTypeElement}

/**
  * @author Aleksander Podkhalyuzin
  * @since 26.04.2009
  */
abstract class TypeParameterInfoTestBase extends ParameterInfoTestBase[ScTypeArgs, ScTypeElement] {

  override protected def folderPath: String = super.folderPath + "typeParameterInfo/"

  override protected def createHandler: ParameterInfoHandlerWithTabActionSupport[ScTypeArgs, Any, ScTypeElement] =
    new ScalaTypeParameterInfoHandler
}