package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import toplevel.{ScTyped, ScNamedElement}
import com.intellij.psi.util.PsiTreeUtil
import toplevel.typedef.ScTypeDefinition
import psi.types.ScDesignatorType

/**
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScSelfTypeElement extends ScNamedElement with ScTyped {
  def typeElement = findChild(classOf[ScTypeElement])


  def calcType() = typeElement match {
    case Some(ste) => ste.cachedType
    case None => {
      val parent = PsiTreeUtil.getParentOfType(this, classOf[ScTypeDefinition])
      assert(parent != null)
      ScDesignatorType(parent)
    }
  }
}