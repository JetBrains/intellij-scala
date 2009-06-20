package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports

import com.intellij.psi.PsiElement
import com.intellij.util.ArrayFactory
import parser.ScalaElementTypes
import stubs.elements.ScImportSelectorsStub
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

class ScImportSelectorsImpl extends ScalaStubBasedElementImpl[ScImportSelectors] with ScImportSelectors {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScImportSelectorsStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ImportSelectors"

  def hasWildcard = findChildByType(ScalaTokenTypes.tUNDER) != null

  def wildcardElement: Option[PsiElement] = {
    if (hasWildcard) Some(findChildByType(ScalaTokenTypes.tUNDER))
    else None
  }

  def selectors: Array[ScImportSelector] = {
    getStubOrPsiChildren(ScalaElementTypes.IMPORT_SELECTOR, new ArrayFactory[ScImportSelector]{
      def create(count: Int): Array[ScImportSelector] = new Array[ScImportSelector](count)
    })
  }
}