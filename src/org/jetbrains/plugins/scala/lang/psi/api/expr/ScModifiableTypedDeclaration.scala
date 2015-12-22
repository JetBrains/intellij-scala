package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 11/26/2015
  */
trait ScModifiableTypedDeclaration extends ScalaPsiElement {

  private[this] var prevModType: ScType = null

  //this function is called only from one place, which makes it fine
  def returnTypeHasChangedSinceLastCheck: Boolean = {
    modifiableReturnType match {
      case Some(retTp) if retTp == prevModType => false
      case Some(retTp) =>
        prevModType = retTp
        true
      case _ => true
    }
  }

  def modifiableReturnType: Option[ScType]
}
