package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumSingletonCase
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScObjectImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.lang.psi.types.api.{Nothing, ParameterizedType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaType}

final class ScEnumSingletonCaseImpl(
  stub: ScTemplateDefinitionStub[ScObject],
  nodeType: ScTemplateDefinitionElementType[ScObject],
  node: ASTNode,
  debugName: String
) extends ScObjectImpl(stub, nodeType, node, debugName)
    with ScEnumCaseImpl
    with ScEnumSingletonCase {

  override protected def targetTokenType: ScalaTokenType = ScalaTokenTypes.kCASE

  def physicalTypeParameters: Seq[ScTypeParam] = Seq.empty

  override def superTypes: List[ScType] =
    if (extendsBlock.templateParents.nonEmpty) super.superTypes
    else {
      val cls = enumParent
      val tps = cls.typeParameters
      if (tps.isEmpty) List(ScalaType.designator(cls))
      else {
        val tpBounds = tps.map(tp =>
          if (tp.isCovariant) tp.lowerBound.getOrNothing
          else if (tp.isContravariant) tp.upperBound.getOrAny
          else Nothing
        )
        List(ParameterizedType(ScalaType.designator(cls), tpBounds))
      }
    }

  protected def parentByStub: PsiElement = getParentByStub

  protected def stubOrPsiParentOfType[E <: PsiElement](aClass: Class[E]): E = getStubOrPsiParentOfType(aClass)
}
