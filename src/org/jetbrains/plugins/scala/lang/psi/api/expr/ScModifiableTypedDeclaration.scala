package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 11/26/2015
  */
trait ScModifiableTypedDeclaration extends ScalaPsiElement {

  private[this] var prevModType: Option[ScType] = null

  //this function is called only from one place, which makes it fine
  def returnTypeHasChangedSinceLastCheck: Boolean = {
    val retTp: Option[ScType] = modifiableReturnType
    if (retTp == prevModType) false
    else {
      prevModType = retTp
      true
    }
  }

  def modifiableReturnType: Option[ScType]
}
