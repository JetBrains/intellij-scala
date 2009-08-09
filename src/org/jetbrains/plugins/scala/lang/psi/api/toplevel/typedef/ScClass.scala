package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import base.{ScPrimaryConstructor, ScModifierList}
import com.intellij.psi.PsiModifierList
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScParameterOwner
import statements.params.ScParameters
/**
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

trait ScClass extends ScTypeDefinition with ScParameterOwner {
  def constructor: Option[ScPrimaryConstructor]

  def clauses: Option[ScParameters] = constructor match {
    case Some(x: ScPrimaryConstructor) => Some(x.parameterList)
    case None => None
  }

}