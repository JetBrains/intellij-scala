package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package templates

import api.base.types.ScSelfTypeElement
import api.statements.{ScDeclaredElementsHolder, ScFunction, ScTypeAlias}
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import parser.ScalaElementTypes
import stubs.ScTemplateBodyStub
import api.expr.ScExpression
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{ResolveState, PsiElement}
import com.intellij.psi.util.PsiTreeUtil
import api.toplevel.typedef.{ScTemplateDefinition, ScMember, ScTypeDefinition}

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
      stub.getChildrenByType(TokenSets.ALIASES_SET, JavaArrayFactoryUtil.ScTypeAliasFactory)
    } else findChildrenByClass(classOf[ScTypeAlias])
  }

  def functions: Array[ScFunction] = getStubOrPsiChildren(TokenSets.FUNCTIONS, JavaArrayFactoryUtil.ScFunctionFactory)

  def typeDefinitions: Seq[ScTypeDefinition] =
    getStubOrPsiChildren(TokenSets.TMPL_DEF_BIT_SET, JavaArrayFactoryUtil.ScTypeDefinitionFactory)

  def members: Array[ScMember] = getStubOrPsiChildren(TokenSets.MEMBERS, JavaArrayFactoryUtil.ScMemberFactory)

  def holders: Array[ScDeclaredElementsHolder] =
    getStubOrPsiChildren(TokenSets.DECLARED_ELEMENTS_HOLDER, JavaArrayFactoryUtil.ScDeclaredElementsHolderFactory)

  def exprs: Array[ScExpression] =
    getStubOrPsiChildren(TokenSets.EXPRESSION_BIT_SET, JavaArrayFactoryUtil.ScExpressionFactory)

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

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState,
                                   lastParent: PsiElement, place: PsiElement): Boolean = {
    val td = PsiTreeUtil.getContextOfType(this, classOf[ScTemplateDefinition])
    if (td != null) {
      if (!td.processDeclarationsForTemplateBody(processor, state, td.extendsBlock, place)) return false
    }
    super.processDeclarations(processor, state, lastParent, place)
  }
}