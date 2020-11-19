package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScEnumCases}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScEnum, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScTypeDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{Nothing, ParameterizedType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaType}
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData

import scala.util.control.NonFatal

final class ScEnumCaseImpl(
  stub:      ScTemplateDefinitionStub[ScEnumCase],
  nodeType:  ScTemplateDefinitionElementType[ScEnumCase],
  node:      ASTNode,
  debugName: String
) extends ScTypeDefinitionImpl(stub, nodeType, node, debugName)
    with ScEnumCase {

  import lexer.ScalaTokenTypes.kCASE

  override def getModifierList: ScModifierList = getParentByStub match {
    case cases: ScEnumCases => cases.getModifierList
    case _ => ScalaPsiElementFactory.createEmptyModifierList(this)
  }

  override def enumParent: Option[ScEnum] =
    this.getStubOrPsiParentOfType(classOf[ScEnum]).toOption

  @CachedInUserData(this, BlockModificationTracker(this))
  override def typeParameters: Seq[ScTypeParam] =
    if (extendsBlock== null && super.typeParameters.isEmpty) {
      try {
        val syntheticClause =
          for {
            e <- enumParent
            tpClause <- e.typeParametersClause
            tpText = tpClause.getTextByStub
          } yield
            ScalaPsiElementFactory.createTypeParameterClauseFromTextWithContext(tpText, this.getContext, this)

        syntheticClause.fold(Seq.empty[ScTypeParam])(_.typeParameters)
      } catch {
        case p: ProcessCanceledException => throw p
        case NonFatal(_)                 => Seq.empty
      }
    } else super.typeParameters

  private def syntheticEnumClass: Option[ScTypeDefinition] =
    enumParent.flatMap(_.syntheticClass)

  override def superTypes: List[ScType] =
    if (extendsBlock.templateParents.nonEmpty) super.superTypes
    else {
      syntheticEnumClass match {
        case Some(cls) =>
          val tps = cls.typeParameters
          if (tps.isEmpty) List(ScalaType.designator(cls))
          else {
            if (constructor.isEmpty) {
              val tpBounds = cls.typeParameters.map(tp =>
                if (tp.isCovariant)          tp.lowerBound.getOrNothing
                else if (tp.isContravariant) tp.upperBound.getOrAny
                else                         Nothing
              )

              List(ParameterizedType(ScDesignatorType(cls), tpBounds))
            } else List(ParameterizedType(ScDesignatorType(cls), typeParameters.map(TypeParameterType(_))))
          }
        case None => List.empty
      }
    }

  override def supers: Seq[PsiClass] =
    if (extendsBlock.templateParents.nonEmpty) super.supers
    else                                       syntheticEnumClass.toSeq

//noinspection TypeAnnotation
  override protected def targetTokenType = kCASE

  //noinspection TypeAnnotation
  override protected def baseIcon = icons.Icons.CLASS; // TODO add an icon
}
