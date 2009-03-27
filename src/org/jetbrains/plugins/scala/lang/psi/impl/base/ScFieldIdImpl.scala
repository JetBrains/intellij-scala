package org.jetbrains.plugins.scala.lang.psi.impl.base

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import api.toplevel.imports.ScImportStmt
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.base._
import _root_.scala.collection.mutable._
import org.jetbrains.plugins.scala.lang.psi.api.statements._

/**
* @author ilyas
*/

class ScFieldIdImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScFieldId {

  override def toString: String = "Field identifier"

  def nameId = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement) = lastParent match {
    case _: ScImportStmt => {
      ScType.extractClassType(calcType) match {
        case Some((c, _)) => c.processDeclarations(processor, state, null, place)
        case _ => true
      }
    }
    case _ => true
  }

}