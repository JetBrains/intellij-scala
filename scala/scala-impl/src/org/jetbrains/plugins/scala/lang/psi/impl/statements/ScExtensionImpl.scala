package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiClass, PsiElement, ResolveState}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType.{EXTENSION_BODY, PARAM_CLAUSES}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtension, ScExtensionBody, ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember.WithBaseIconProvider
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScExtensionStub

import javax.swing.Icon

class ScExtensionImpl(@Nullable stub: ScExtensionStub, @Nullable node: ASTNode)
    extends ScalaStubBasedElementImpl(stub, ScalaElementType.EXTENSION, node)
    with ScExtension
    with ScTypeParametersOwner
    with WithBaseIconProvider {

  override def toString: String = "Extension on " + targetTypeElement.fold("<unknown>")(_.getText)

  override protected final def baseIcon: Icon =
    Icons.EXTENSION

  override def targetParameter: Option[ScParameter] =
    allClauses.find(!_.isUsing).flatMap(_.parameters.headOption)

  override def targetTypeElement: Option[ScTypeElement] =
    targetParameter.flatMap(_.typeElement)

  override def declaredElements: Seq[ScFunction] = extensionMethods

  override def extensionMethods: Seq[ScFunction] =
    extensionBody.fold(Seq.empty[ScFunction])(_.functions)

  override def parameters: Seq[ScParameter] =
    clauses.toSeq.flatMap(_.clauses.flatMap(_.parameters))

  override def clauses: Option[ScParameters]          = getStubOrPsiChild(PARAM_CLAUSES).toOption
  override def extensionBody: Option[ScExtensionBody] = getStubOrPsiChild(EXTENSION_BODY).toOption

  override def getContainingClass: PsiClass = null

  override def hasModifierProperty(name: String): Boolean = false

  override def processDeclarations(
    processor:  PsiScopeProcessor,
    state:      ResolveState,
    lastParent: PsiElement,
    place:      PsiElement
  ): Boolean = {
    if (!super[ScTypeParametersOwner].processDeclarations(processor, state, lastParent, place))
      return false

    for {
      clause <- effectiveParameterClauses
      param  <- clause.effectiveParameters
    } {
      ProgressManager.checkCanceled()
      if (!processor.execute(param, state)) return false
    }

    true
  }

  override protected def markerElementType: IElementType = ScalaTokenType.ExtensionKeyword

  override protected def endParent: Option[PsiElement] = extensionBody
}

