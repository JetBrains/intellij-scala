package org.jetbrains.plugins.scala
package lang
package parameterInfo
package patternParameterInfo

import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithTabActionSupport
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScPattern, ScPatternArgumentList}

/**
  * @author Aleksander Podkhalyuzin
  * @since 25.04.2009
  */
abstract class PatternParameterInfoTestBase extends ParameterInfoTestBase[ScPatternArgumentList, ScPattern] {

  override protected def folderPath = super.folderPath + "patternParameterInfo/"

  override protected def createHandler: ParameterInfoHandlerWithTabActionSupport[ScPatternArgumentList, Any, ScPattern] =
    new ScalaPatternParameterInfoHandler
}