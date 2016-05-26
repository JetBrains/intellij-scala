package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper

/** 
* @author Alexander Podkhalyuzin
* @since 20.02.2008
*/

trait ScTrait extends ScTypeDefinition {
  def getObjectClassOrTraitToken = findFirstChildByType(ScalaTokenTypes.kTRAIT)

  def fakeCompanionClass = new PsiClassWrapper(this, getQualifiedName + "$class", getName + "$class")
}