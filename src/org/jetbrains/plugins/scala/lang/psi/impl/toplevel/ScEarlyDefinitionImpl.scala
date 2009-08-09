package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel

import com.intellij.psi.PsiElement
import stubs.ScEarlyDefinitionsStub;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import org.jetbrains.plugins.scala.icons.Icons

import org.jetbrains.plugins.scala.lang.psi.api.toplevel._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScEarlyDefinitionsImpl extends ScalaStubBasedElementImpl[ScEarlyDefinitions] with ScEarlyDefinitions {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScEarlyDefinitionsStub) = {this(); setStub(stub); setNode(null)}
  override def toString: String = "EarlyDefinitions"
}