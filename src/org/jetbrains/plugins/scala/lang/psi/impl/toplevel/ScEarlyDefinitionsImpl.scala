package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel

import com.intellij.psi.PsiElement
import stubs.ScEarlyDefinitionsStub
import api.statements.{ScVariableDefinition, ScPatternDefinition}

import stubs.ScEarlyDefinitionsStub
import com.intellij.psi.{ResolveState, PsiElement}
import com.intellij.psi.scope.PsiScopeProcessor
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
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScEarlyDefinitionsImpl private () extends ScalaStubBasedElementImpl[ScEarlyDefinitions] with ScEarlyDefinitions {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScEarlyDefinitionsStub) = {this(); setStub(stub); setNode(null)}
  override def toString: String = "EarlyDefinitions"

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState,
                                   lastParent: PsiElement, place: PsiElement): Boolean = {
    var element: PsiElement = lastParent
    while (element != null) {
      element match {
        case p: ScPatternDefinition => {
          val iterator = p.bindings.iterator
          while (iterator.hasNext) {
            val elem = iterator.next
            if (!processor.execute(elem, state)) return false
          }
        }
        case p: ScVariableDefinition => {
          val iterator = p.bindings.iterator
          while (iterator.hasNext) {
            val elem = iterator.next
            if (!processor.execute(elem, state)) return false
          }
        }
        case _ =>
      }
      element = element.getPrevSibling
    }
    return true
  }

  def members: Seq[ScMember] = {
    getStub match {
      case stub: ScEarlyDefinitionsStub =>
        import scala.collection.JavaConverters._
        for {
          child <- stub.getChildrenStubs.asScala
          psi = child.getPsi
          if psi.isInstanceOf[ScMember]
        } yield psi.asInstanceOf[ScMember]
      case _ => findChildrenByClassScala(classOf[ScMember])
    }
  }
}