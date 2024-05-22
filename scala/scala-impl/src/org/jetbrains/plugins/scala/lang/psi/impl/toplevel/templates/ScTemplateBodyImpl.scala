package org.jetbrains.plugins.scala.lang.psi.impl.toplevel
package templates

import com.intellij.lang.ASTNode
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil._
import org.jetbrains.plugins.scala.caches.{ModTracker, cached}
import org.jetbrains.plugins.scala.lang.TokenSets._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType.{EXTENSION, EnumCases, SELF_TYPE, TEMPLATE_BODY}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScEnumCases, ScExtension, ScFunction, ScTypeAlias, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScTemplateDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateBodyStub

class ScTemplateBodyImpl private(stub: ScTemplateBodyStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, TEMPLATE_BODY, node)
    with ScTemplateBody {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScTemplateBodyStub) = this(stub, null)

  override def toString: String = "ScTemplateBody"

  override def aliases: Seq[ScTypeAlias] =
    getStubOrPsiChildren(ALIASES_SET, ScTypeAliasFactory).toSeq

  override def cases: Seq[ScEnumCases] =
    getStubOrPsiChildren(EnumCases, ScEnumCasesFactory).toSeq

  override def functions: Seq[ScFunction] =
    getStubOrPsiChildren(FUNCTIONS, ScFunctionFactory).toSeq.filterNot(_.isLocal)

  override def properties: Seq[ScValueOrVariable] =
    getStubOrPsiChildren(PROPERTIES, ScPropertyFactory)
      .toSeq
      .filterNot(_.isLocal)

  override def typeDefinitions: Seq[ScTypeDefinition] =
    getStubOrPsiChildren(TYPE_DEFINITIONS, ScTypeDefinitionFactory)
      .toSeq.filterNot(_.isLocal)

  override def members: Seq[ScMember] =
    getStubOrPsiChildren(MEMBERS, ScMemberFactory).toSeq.filterNot(_.isLocal)

  override def holders: Seq[ScDeclaredElementsHolder] =
    getStubOrPsiChildren(DECLARED_ELEMENTS_HOLDER, ScDeclaredElementsHolderFactory).toSeq.filterNot {
      case s: ScMember => s.isLocal
      case _ => false
    }

  override def extensions: Seq[ScExtension] =
    getStubOrPsiChildren(EXTENSION, ScExtensionFactory).toSeq.filterNot(_.isLocal)

  override def exprs: Seq[ScExpression] =
    if (this.getStub != null) Seq.empty //we don't have stubbed expressions
    else findChildren[ScExpression]

  override def selfTypeElement: Option[ScSelfTypeElement] = _selfTypeElement()

  private val _selfTypeElement = cached("selfTypeElement", ModTracker.anyScalaPsiChange, () => {
    Option(getStubOrPsiChild(SELF_TYPE))
  })

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    val td = PsiTreeUtil.getContextOfType(this, classOf[ScTemplateDefinitionImpl[_]])
    if (td != null) {
      if (!td.processDeclarationsForTemplateBody(processor, state, td.extendsBlock, place))
        return false
    }
    processDeclarationsFromImports(processor, state, lastParent, place)
  }

  override def controlFlowScope: Option[ScalaPsiElement] = Some(this)

  override protected def childBeforeFirstImport: Option[PsiElement] = {
    selfTypeElement.orElse(super.childBeforeFirstImport)
  }

  override def isEmpty: Boolean = _isEmpty()

  private val _isEmpty = cached("isEmpty", ModTracker.anyScalaPsiChange, () => {
    getStubOrPsiChildren(TokenSet.ANY, PsiElementFactory)
      .forall { child =>
        val node = child.getNode
        node != null && ScTemplateBodyImpl.WHITESPACE_OR_BRACE.contains(node.getElementType)
      }
  })
}

object ScTemplateBodyImpl {
  private val WHITESPACE_OR_BRACE = TokenSet.orSet(TokenSet.WHITE_SPACE, ScalaTokenTypes.BRACES_TOKEN_SET)
}
