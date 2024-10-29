package org.jetbrains.plugins.scala.lang.psi.impl.toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressManager}
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiElement, PsiNamedElement, ResolveState}
import org.jetbrains.plugins.scala.caches.{ModTracker, cached, cachedInUserData}
import org.jetbrains.plugins.scala.extensions.{Model, ObjectExt, StringsExt}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType.PARAM_CLAUSES
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScGivenDefinition, ScMember}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, canNotBeOverridden}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.ScalaPsiElementCreationException
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScNamedBeginImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

import javax.swing.Icon

class ScGivenDefinitionImpl(
  stub:      ScTemplateDefinitionStub[ScGivenDefinition],
  nodeType:  ScTemplateDefinitionElementType[ScGivenDefinition],
  node:      ASTNode,
  debugName: String
) extends ScTypeDefinitionImpl(stub, nodeType, node, debugName)
    with ScGivenImpl
    with ScGivenDefinition
    with ScNamedBeginImpl {

  override protected def baseIcon: Icon = Icons.CLASS // todo: better icon ?

  override protected def targetTokenType: ScalaTokenType = ScalaTokenType.GivenKeyword

  override def declaredElements: Seq[PsiNamedElement] = Seq(this)

  override def isObject: Boolean = typeParametersClause.isEmpty && parameters.isEmpty

  override def nameId: PsiElement = nameElement.getOrElse(extendsBlock)

  override def givenType(): TypeResult =
    typeElements
      .headOption
      .map(_.`type`())
      .getOrElse(`type`())

  override protected def nameInner: String =
    nameElement
      .map(_.getText)
      .getOrElse(ScalaPsiUtil.generateGivenName(typeElements: _*))

  private def typeElements: Seq[ScTypeElement] =
    extendsBlock
      .templateParents
      .toSeq
      .flatMap(_.typeElements)

  override def clauses: Option[ScParameters] = _clauses()

  private val _clauses = cached("clauses", ModTracker.anyScalaPsiChange, () => {
    getStubOrPsiChild(PARAM_CLAUSES).toOption
  })

  override def parameters: Seq[ScParameter] =
    clauses.fold(Seq.empty[ScParameter])(_.params)

  def parametersText: String = byStubOrPsi(_.givenDefinitionParameterText) {
    clauses.fold("") { parameterClauses =>
      parameterClauses.clauses.foldLeft("") {
        case (acc, singleClause) =>
          // is this a pre 3.6 normal parameter clause
          val isOldStyleParameterClause =
            singleClause.getFirstChild.getNode.getElementType == ScalaTokenTypes.tLPARENTHESIS &&
              singleClause.getLastChild.getNode.getElementType == ScalaTokenTypes.tRPARENTHESIS

          val clauseText =
            if (isOldStyleParameterClause) singleClause.getText
            else { // 3.6+ case, when parameters are introduced as conditionals with `=>`
              val parametersString = singleClause.parameters.map(_.getText).mkString(", ")
              s"(using $parametersString)"
            }

          acc + clauseText
      }
    }
  }

  override def desugaredDefinitions: Seq[ScMember] =
    cachedInUserData("desugaredDefinitions", this, ModTracker.libraryAware(this)) {
      try {
        val supersText = extendsBlock.templateParents.fold("")(_.supersText)

        if (isObject) {
          val text = s"implicit object $name extends $supersText"
          val obj  = ScalaPsiElementFactory.createTypeDefinitionWithContext(text, this.getContext, this)
          obj.originalGivenElement       = this
          obj.syntheticNavigationElement = this
          Seq(obj)
        } else {
          val typeParametersDeclText = typeParametersClause.fold("")(_.getTextByStub)

          val typeParametersText =
            if (typeParameters.isEmpty) ""
            else                        typeParameters.map(_.name).commaSeparated(Model.SquareBrackets)

          val parametersText = this.parametersText

          val clsText            = s"class $name$typeParametersDeclText$parametersText extends $supersText"
          val implicitMethodText = s"implicit def $name$typeParametersDeclText$parametersText: $name$typeParametersText = ???"

          val cls = ScalaPsiElementFactory.createTypeDefinitionWithContext(clsText, this.getContext, this)
          cls.originalGivenElement       = this
          cls.syntheticNavigationElement = this

          val implicitMethod = ScalaPsiElementFactory.createDefinitionWithContext(implicitMethodText, this.getContext, this)
          implicitMethod.originalGivenElement       = this
          implicitMethod.syntheticNavigationElement = this

          Seq(cls, implicitMethod)
        }
      } catch {
        case p: ProcessCanceledException         => throw p
        case _: ScalaPsiElementCreationException => Seq.empty
      }
    }

  override def processDeclarations(
    processor:  PsiScopeProcessor,
    state:      ResolveState,
    lastParent: PsiElement,
    place:      PsiElement
  ): Boolean = {
    if (!processParameters(processor, state)) false
    else
      super.processDeclarations(processor, state, lastParent, place)
  }

  override protected def keywordTokenType: IElementType = ScalaTokenType.GivenKeyword

  // TODO Why ScGiven is a subtype of ScNamedElement it there might be no name?
  override def namedTag: Option[ScNamedElement] = if (nameElement.isDefined) Some(this) else None

  override protected def endParent: Option[PsiElement] = extendsBlock.templateBody


  override def isEffectivelyFinal: Boolean = true
}
