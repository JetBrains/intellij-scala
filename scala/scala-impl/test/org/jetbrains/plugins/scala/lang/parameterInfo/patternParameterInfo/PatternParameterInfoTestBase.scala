package org.jetbrains.plugins.scala
package lang
package parameterInfo
package patternParameterInfo

import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithTabActionSupport
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPatternArgumentList

/**
  * @author Aleksander Podkhalyuzin
  * @since 25.04.2009
  */
abstract class PatternParameterInfoTestBase extends ParameterInfoTestBase[ScPatternArgumentList] {

  override def getTestDataPath: String =
    s"${super.getTestDataPath}patternParameterInfo/"

  override protected def createHandler: ParameterInfoHandlerWithTabActionSupport[ScPatternArgumentList, Any, _ <: PsiElement] =
    new ScalaPatternParameterInfoHandler
}