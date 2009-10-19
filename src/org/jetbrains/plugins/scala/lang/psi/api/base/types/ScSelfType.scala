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
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScSelfTypeElement extends ScNamedElement with ScTypedDefinition {
  def typeElement = findChild(classOf[ScTypeElement])


  def calcType() = typeElement match {
    case Some(ste) => ste.cachedType.unwrap(Any)
    case None => {
      val parent = PsiTreeUtil.getParentOfType(this, classOf[ScTypeDefinition])
      assert(parent != null)
      ScDesignatorType(parent)
    }
  }
}