package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumClassCase
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScClassImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaType}

final class ScEnumClassCaseImpl(
  stub:      ScTemplateDefinitionStub[ScClass],
  nodeType:  ScTemplateDefinitionElementType[ScClass],
  node:      ASTNode,
  debugName: String
) extends ScClassImpl(stub, nodeType, node, debugName)
    with ScEnumCaseImpl
    with ScTypeParametersOwner
    with ScEnumClassCase {

  override protected def targetTokenType: ScalaTokenType = ScalaTokenTypes.kCASE

  private def enumTypeParameters: Seq[ScTypeParam] =
    enumParent.typeParametersClause.map(_.typeParameters).getOrElse(Seq.empty)

  def physicalTypeParameters: Seq[ScTypeParam] = super.typeParameters

  override def typeParameters: Seq[ScTypeParam] =
    if (enumTypeParameters.nonEmpty && physicalTypeParameters.isEmpty && (extendsBlock.templateParents.isEmpty || mentionsEnumTypeParameters))
      enumTypeParameters
    else
      physicalTypeParameters

  private def mentionsEnumTypeParameters: Boolean = {
    val tps = enumTypeParameters

    (constructor.map(_.parameterList) ++ extendsBlock.templateParents).flatMap(_.elements).exists {
      case ScSimpleTypeElement(r) if r.qualifier.isEmpty => tps.exists(_.name == r.refName)
      case _ => false
    }
  }

  override def superTypes: List[ScType] =
    if (extendsBlock.templateParents.nonEmpty) super.superTypes
    else {
      val cls = enumParent
      if (cls.typeParameters.isEmpty) List(ScalaType.designator(cls))
      else List(ParameterizedType(ScalaType.designator(cls), typeParameters.map(TypeParameterType(_))))
    }

  protected def parentByStub: PsiElement = super.getParentByStub

  protected def stubOrPsiParentOfType[E <: PsiElement](aClass: Class[E]): E = super.getStubOrPsiParentOfType(aClass)
}
