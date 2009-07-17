package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import com.intellij.extapi.psi.ASTDelegatePsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.types._

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

trait ScTypedPattern extends ScBindingPattern  {
  def typePattern = findChild(classOf[ScTypePattern])

  override def calcType = typePattern match {
    case None => Nothing
    case Some(tp) => tp.typeElement.cachedType
  }
}