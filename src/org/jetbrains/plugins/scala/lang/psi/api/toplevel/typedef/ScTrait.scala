package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import lexer.ScalaTokenTypes
import com.intellij.psi.{PsiClass, PsiElement}

/** 
* @author Alexander Podkhalyuzin
* @since 20.02.2008
*/

trait ScTrait extends ScTypeDefinition {
  def getTraitToken: PsiElement = findFirstChildByType(ScalaTokenTypes.kTRAIT)
  def getObjectClassOrTraitToken = getTraitToken
  def fakeCompanionClass: PsiClass
}