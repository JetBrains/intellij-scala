package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.{PsiElement, PsiModifierList}
import lexer.ScalaTokenTypes

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

trait ScTrait extends ScTypeDefinition {
  def getTraitToken: PsiElement = findFirstChildByType(ScalaTokenTypes.kTRAIT)
  def getObjectClassOrTraitToken = getTraitToken
}