package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.caches.ModTracker
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType.PARAM_CLAUSES
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScGivenDefinition, ScMember}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, CachedInUserData}

import javax.swing.Icon

class ScGivenDefinitionImpl(
  stub:      ScTemplateDefinitionStub[ScGivenDefinition],
  nodeType:  ScTemplateDefinitionElementType[ScGivenDefinition],
  node:      ASTNode,
  debugName: String
) extends ScTypeDefinitionImpl(stub, nodeType, node, debugName)
    with ScGivenImpl
    with ScGivenDefinition {

  override protected def baseIcon: Icon = Icons.CLASS // todo: better icon ?

  override protected def targetTokenType: ScalaTokenType = ScalaTokenType.GivenKeyword

  override def declaredElements: Seq[PsiNamedElement] = Seq(this)

  override def isObject: Boolean = typeParametersClause.isEmpty && parameters.isEmpty

  override def nameId: PsiElement = nameElement.getOrElse(extendsBlock)

  override def nameInner: String = {
    val explicitName = nameElement.map(_.getText)
    val typeElements = extendsBlock.templateParents.toSeq.flatMap(_.typeElements)

    explicitName
      .getOrElse(ScalaPsiUtil.generateGivenOrExtensionName(typeElements: _*))
  }

  @Cached(ModTracker.anyScalaPsiChange, this)
  override def clauses: Option[ScParameters] =
    getStubOrPsiChild(PARAM_CLAUSES).toOption

  override def parameters: Seq[ScParameter] =
    clauses.fold(Seq.empty[ScParameter])(_.params)

  @CachedInUserData(this, ModTracker.libraryAware(this))
  override def desugaredDefinitions: Seq[ScMember] = {
    val supersText = extendsBlock.templateParents.fold("")(_.getText)

    if (isObject) {
      val text = s"implicit object $name extends $supersText"
      val obj  = ScalaPsiElementFactory.createTypeDefinitionWithContext(text, this.getContext, this)
      obj.originalGivenElement       = this
      obj.syntheticNavigationElement = this
      Seq(obj)
    } else {
      val typeParametersText = typeParametersClause.fold("")(_.getTextByStub)
      val parametersText     = clauses.fold("")(_.getText)

      val clsText            = s"class $name$typeParametersText$parametersText extends $supersText"
      val implicitMethodText = s"implicit def $name$typeParametersText$parametersText: $name$typeParametersText = ???"

      val cls = ScalaPsiElementFactory.createTypeDefinitionWithContext(clsText, this.getContext, this)
      cls.originalGivenElement       = this
      cls.syntheticNavigationElement = this

      val implicitMethod = ScalaPsiElementFactory.createDefinitionWithContext(implicitMethodText, this.getContext, this)
      implicitMethod.originalGivenElement       = this
      implicitMethod.syntheticNavigationElement = this

      Seq(cls, implicitMethod)
    }
  }
}
