package org.jetbrains.plugins.scala.lang.refactoring.extractMethod

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.03.2010
 */

case class ExtractMethodReturn(oldParamName: String, returnType: ScType, needNewDefinition: Boolean, 
                               isVal: Boolean)