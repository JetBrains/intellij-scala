package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package templates

import api.base.types.ScSelfTypeElement
import api.statements.{ScDeclaredElementsHolder, ScFunction, ScTypeAlias}
import api.toplevel.typedef.{ScMember, ScTypeDefinition}
import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.util.ArrayFactory
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import parser.ScalaElementTypes
import stubs.{ScTemplateBodyStub, ScPrimaryConstructorStub}

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:38:04
*/

class ScTemplateBodyImpl extends ScalaStubBasedElementImpl[ScTemplateBody] with ScTemplateBody
                                        with ScImportsHolder {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTemplateBodyStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ScTemplateBody"

  def aliases: Array[ScTypeAlias] = {
    val stub = getStub
    if (stub != null) {
      stub.getChildrenByType(TokenSets.ALIASES_SET, new ArrayFactory[ScTypeAlias] {
        def create(count: Int): Array[ScTypeAlias] = new Array[ScTypeAlias](count)
      })
    } else findChildrenByClass(classOf[ScTypeAlias])
  }

  def functions: Array[ScFunction] = {
    getStubOrPsiChildren(TokenSets.FUNCTIONS, new ArrayFactory[ScFunction] {
      def create(count: Int): Array[ScFunction] = new Array[ScFunction](count)
    })
  }


  def typeDefinitions: Seq[ScTypeDefinition] = {
    getStubOrPsiChildren[ScTypeDefinition](TokenSets.TMPL_DEF_BIT_SET, new ArrayFactory[ScTypeDefinition] {
      def create(count: Int): Array[ScTypeDefinition] = new Array[ScTypeDefinition](count)
    })
  }


  def members: Array[ScMember] = {
    getStubOrPsiChildren(TokenSets.MEMBERS, new ArrayFactory[ScMember] {
      def create(count: Int): Array[ScMember] = new Array[ScMember](count)
    })
  }


  def holders: Array[ScDeclaredElementsHolder] = {
    getStubOrPsiChildren(TokenSets.DECLARED_ELEMENTS_HOLDER, new ArrayFactory[ScDeclaredElementsHolder] {
      def create(count: Int): Array[ScDeclaredElementsHolder] = new Array[ScDeclaredElementsHolder](count)
    })
  }


  def selfTypeElement: Option[ScSelfTypeElement] = {
    val stub = getStub
    if (stub != null) {
      stub.findChildStubByType(ScalaElementTypes.SELF_TYPE) match {
        case null => return None
        case s => return Some(s.getPsi)
      }
    }
    findChildByType(ScalaElementTypes.SELF_TYPE) match {
      case null => None
      case s => Some(s.asInstanceOf[ScSelfTypeElement])
    }
  }
}