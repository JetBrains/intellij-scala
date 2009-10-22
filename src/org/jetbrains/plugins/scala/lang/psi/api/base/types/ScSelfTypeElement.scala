package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import toplevel.{ScTypedDefinition, ScNamedElement}
import com.intellij.psi.util.PsiTreeUtil
import toplevel.typedef.ScTypeDefinition
import psi.types.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.Any
import psi.types.result.Success

/**
* @author ilyas, Alexander Podkhalyuzin
*/

trait ScSelfTypeElement extends ScNamedElement with ScTypedDefinition {
  def typeElement = findChild(classOf[ScTypeElement])
}