package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package templates

import com.intellij.lang.ASTNode
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateBodyStub
import org.jetbrains.plugins.scala.project.ProjectExt

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:38:04
*/

class ScTemplateBodyImpl private (stub: StubElement[ScTemplateBody], nodeType: IElementType, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node) with ScTemplateBody
                                        with ScImportsHolder {
  def this(node: ASTNode) = {this(null, null, node)}
  def this(stub: ScTemplateBodyStub) = {this(stub, ScalaElementTypes.TEMPLATE_BODY, null)}

  override def toString: String = "ScTemplateBody"

  def aliases: Array[ScTypeAlias] = {
    val stub = getStub
    if (stub != null) {
      stub.getChildrenByType(TokenSets.ALIASES_SET, JavaArrayFactoryUtil.ScTypeAliasFactory)
    } else findChildrenByClass(classOf[ScTypeAlias])
  }

  def functions: Array[ScFunction] = getStubOrPsiChildren(TokenSets.FUNCTIONS, JavaArrayFactoryUtil.ScFunctionFactory).filterNot(_.isLocal)

  def typeDefinitions: Seq[ScTypeDefinition] =
    getStubOrPsiChildren(getProject.tokenSets.templateDefinitionSet, JavaArrayFactoryUtil.ScTypeDefinitionFactory).filterNot(_.isLocal)

  def members: Array[ScMember] = getStubOrPsiChildren(getProject.tokenSets.membersSet, JavaArrayFactoryUtil.ScMemberFactory).filterNot(_.isLocal)

  def holders: Array[ScDeclaredElementsHolder] =
    getStubOrPsiChildren(TokenSets.DECLARED_ELEMENTS_HOLDER, JavaArrayFactoryUtil.ScDeclaredElementsHolderFactory).filterNot {
      case s: ScMember => s.isLocal
      case _ => false
    }

  def exprs: Array[ScExpression] =
    getStubOrPsiChildren(TokenSets.EXPRESSION_BIT_SET, JavaArrayFactoryUtil.ScExpressionFactory).filterNot {
      case s: ScMember => s.isLocal
      case _ => false
    }

  def selfTypeElement: Option[ScSelfTypeElement] = {
    val stub = getStub
    if (stub != null) {
      stub.findChildStubByType(ScalaElementTypes.SELF_TYPE) match {
        case null => return None
        case s => return Some(s.getPsi)
      }
    }
    Option(findChildByType[ScSelfTypeElement](ScalaElementTypes.SELF_TYPE))
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState,
                                   lastParent: PsiElement, place: PsiElement): Boolean = {
    val td = PsiTreeUtil.getContextOfType(this, classOf[ScTemplateDefinition])
    if (td != null) {
      if (!td.processDeclarationsForTemplateBody(processor, state, td.extendsBlock, place)) return false
    }
    super.processDeclarations(processor, state, lastParent, place)
  }

  override def controlFlowScope: Option[ScalaPsiElement] = Some(this)

  override protected def childBeforeFirstImport: Option[PsiElement] = {
    selfTypeElement.orElse(super.childBeforeFirstImport)
  }
}