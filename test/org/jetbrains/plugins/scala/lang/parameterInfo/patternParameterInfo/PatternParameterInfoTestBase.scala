package org.jetbrains.plugins.scala
package lang
package parameterInfo
package patternParameterInfo

import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithTabActionSupport
import com.intellij.psi.PsiElement

/**
  * @author Aleksander Podkhalyuzin
  * @since 25.04.2009
  */
abstract class PatternParameterInfoTestBase extends ParameterInfoTestBase {

  override protected def folderPath = super.folderPath + "patternParameterInfo/"

  override protected def createHandler: ParameterInfoHandlerWithTabActionSupport[_ <: PsiElement, Any, _ <: PsiElement] =
    new ScalaPatternParameterInfoHandler
}