package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

/** 
* @author Alexander Podkhalyuzin
* @since 20.02.2008
*/
trait ScTrait extends ScTypeDefinition {

  def getTraitToken: PsiElement = findFirstChildByType(ScalaTokenTypes.kTRAIT)

  def getObjectClassOrTraitToken: PsiElement = getTraitToken

  def fakeCompanionClass: PsiClass
}