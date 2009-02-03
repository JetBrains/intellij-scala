package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiIdentifier, PsiElement}
import java.lang.String

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.02.2009
 */

class ScScriptClassImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with PsiClassFake {
  def setName(name: String): PsiElement = this

  def getNameIdentifier: PsiIdentifier = null
}