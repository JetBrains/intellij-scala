package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package templates

import com.intellij.lang.ASTNode
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil._
import org.jetbrains.plugins.scala.lang.TokenSets._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes.{SELF_TYPE, TEMPLATE_BODY}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateBodyStub

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:38:04
*/

class ScTemplateBodyImpl private (stub: ScTemplateBodyStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, TEMPLATE_BODY, node) with ScTemplateBody
                                        with ScImportsHolder {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScTemplateBodyStub) = this(stub, null)

  override def toString: String = "ScTemplateBody"

  def aliases: Seq[ScTypeAlias] =
    getStubOrPsiChildren(ALIASES_SET, ScTypeAliasFactory)

  def functions: Seq[ScFunction] =
    getStubOrPsiChildren(FUNCTIONS, ScFunctionFactory).toSeq.filterNot(_.isLocal)

  def typeDefinitions: Seq[ScTypeDefinition] =
    getStubOrPsiChildren(TYPE_DEFINITIONS, ScTypeDefinitionFactory)
      .toSeq.filterNot(_.isLocal)

  def members: Seq[ScMember] =
    getStubOrPsiChildren(MEMBERS, ScMemberFactory).toSeq.filterNot(_.isLocal)

  def holders: Seq[ScDeclaredElementsHolder] =
    getStubOrPsiChildren(DECLARED_ELEMENTS_HOLDER, ScDeclaredElementsHolderFactory).toSeq.filterNot {
      case s: ScMember => s.isLocal
      case _ => false
    }

  def exprs: Seq[ScExpression] =
    getStubOrPsiChildren(EXPRESSION_SET, ScExpressionFactory).toSeq

  def selfTypeElement: Option[ScSelfTypeElement] =
    Option(getStubOrPsiChild(SELF_TYPE))

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