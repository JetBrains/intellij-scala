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

class ScImportSelectorsImpl private (stub: ScImportSelectorsStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementTypes.IMPORT_SELECTORS, node) with ScImportSelectors {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScImportSelectorsStub) = this(stub, null)

  override def toString: String = "ImportSelectors"

  def hasWildcard: Boolean = byStubOrPsi(_.hasWildcard)(wildcardElement.nonEmpty)

  def wildcardElement: Option[PsiElement] = Option(findChildByType(ScalaTokenTypes.tUNDER))

  def selectors: Array[ScImportSelector] =
    getStubOrPsiChildren(ScalaElementTypes.IMPORT_SELECTOR, JavaArrayFactoryUtil.ScImportSelectorFactory)
}