package org.jetbrains.plugins.scala.lang.refactoring.extractMethod

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.03.2010
 */

case class ExtractMethodParameter(oldName: String, newName: String, isRef: Boolean, tp: ScType,
                                  needMirror: Boolean, passAsParameter: Boolean, isFunction: Boolean)