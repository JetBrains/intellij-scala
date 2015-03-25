package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package imports

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScImportSelectorsStub

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

class ScImportSelectorsImpl extends ScalaStubBasedElementImpl[ScImportSelectors] with ScImportSelectors {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScImportSelectorsStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ImportSelectors"

  def hasWildcard: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScImportSelectorsStub].hasWildcard
    }
    findChildByType[PsiElement](ScalaTokenTypes.tUNDER) != null
  }

  def wildcardElement: Option[PsiElement] = {
    if (hasWildcard) Some(findChildByType[PsiElement](ScalaTokenTypes.tUNDER))
    else None
  }

  def selectors: Array[ScImportSelector] =
    getStubOrPsiChildren(ScalaElementTypes.IMPORT_SELECTOR, JavaArrayFactoryUtil.ScImportSelectorFactory)
}