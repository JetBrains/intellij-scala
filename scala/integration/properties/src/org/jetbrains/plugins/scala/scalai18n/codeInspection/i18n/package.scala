package org.jetbrains.plugins.scala
package scalai18n
package codeInspection

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass

package object i18n {
  def originalCaseClassConstructor(applyOrUnapply: ScFunction): Option[ScPrimaryConstructor] = for {
    obj <- applyOrUnapply.containingClass.toOption
    td <- ScalaPsiUtil.getCompanionModule(obj)
    clazz <- td.asOptionOf[ScClass]
    if clazz.isCase
    constructor <- clazz.constructor
  } yield constructor

  def originalCaseClassParameter(applyOrUnapply: ScFunction, paramIdx: Int): Option[ScClassParameter] = for {
    constructor <- originalCaseClassConstructor(applyOrUnapply)
    param <- constructor.parameters.lift(paramIdx)
  } yield param
}
