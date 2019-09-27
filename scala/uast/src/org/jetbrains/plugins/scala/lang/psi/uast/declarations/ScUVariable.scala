package org.jetbrains.plugins.scala.lang.psi.uast.declarations

import _root_.java.util

import com.intellij.psi.util.PsiTreeUtil._
import com.intellij.psi.{
  PsiElement,
  PsiField,
  PsiLocalVariable,
  PsiModifier,
  PsiType,
  PsiVariable
}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{
  ScMethodLike,
  ScModifierList
}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{
  ScBlockStatement,
  ScExpression
}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{
  ScPatternDefinition,
  ScValueOrVariable,
  ScVariableDefinition
}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.ScUElement
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.plugins.scala.lang.psi.uast.psi.LightVariableWithGivenAnnotationsBuilder
import org.jetbrains.uast._

import scala.collection.JavaConverters._
import scala.collection.mutable

trait ScUVariableCommon extends ScUElement with UAnchorOwner with UAnnotated {

  //region Abstract elements section
  protected def lightVariable: PsiVariable
  protected def nameId: PsiElement
  protected def typeElem: Option[ScTypeElement]
  protected def initializer: Option[ScBlockStatement]
  //endregion

  @Nullable
  def getTypeReference: UTypeReferenceExpression =
    typeElem.flatMap(_.convertTo[UTypeReferenceExpression](this)).orNull

  @Nullable
  def getUastInitializer: UExpression =
    initializer.map(_.convertToUExpressionOrEmpty(this)).orNull

  override def getUastAnchor: UIdentifier = createUIdentifier(nameId, this)

  override def getUAnnotations: util.List[UAnnotation] =
    lightVariable.getAnnotations
      .flatMap(_.convertTo[UAnnotation](this))
      .toSeq
      .asJava
}

/**
  * Light adapter for the [[UField]]
  *
  * @param lightVariable Psi field adapter of the field-like class member
  * @param nameId        Psi anchor representing name id
  */
final class ScUField(override protected val lightVariable: PsiField,
                     @Nullable sourcePsi: PsiElement,
                     override protected val nameId: PsiElement,
                     override protected val typeElem: Option[ScTypeElement],
                     override protected val initializer: Option[ScExpression],
                     override protected val parent: LazyUElement)
    extends UFieldAdapter(lightVariable)
    with ScUVariableCommon {

  override type PsiFacade = PsiField

  override protected val scElement: PsiFacade = lightVariable

  @Nullable
  override def getSourcePsi: PsiElement = sourcePsi
}

/**
  * Light adapter for the [[ULocalVariable]]
  *
  * @param lightVariable Psi local variable adapter
  * @param nameId        Psi anchor representing name id
  */
final class ScULocalVariable(
  override protected val lightVariable: PsiLocalVariable,
  @Nullable sourcePsi: PsiElement,
  override protected val nameId: PsiElement,
  override protected val typeElem: Option[ScTypeElement],
  override protected val initializer: Option[ScExpression],
  override protected val parent: LazyUElement
) extends ULocalVariableAdapter(lightVariable)
    with ScUVariableCommon {

  override type PsiFacade = PsiLocalVariable

  override protected val scElement: PsiFacade = lightVariable

  @Nullable
  override def getSourcePsi: PsiElement = sourcePsi
}

object ScUVariable {

  private[uast] def createLightVariable(
    isField: Boolean,
    name: String,
    psiType: PsiType,
    isVal: Boolean,
    containingTypeDef: ScTemplateDefinition,
    modifiersList: Option[ScModifierList]
  ): PsiField with PsiLocalVariable = {

    val javaModifiers = mutable.ArrayBuffer.empty[String]

    // process annotations
    // modifiers ++= modifiersList.getAnnotations.map(a => s"@${a.getQualifiedName}")
    /**
      * Because parsing of annotations is unsupported currently in IDEA 2019.3
      * annotations will be given to the custom wrapper [[LightVariableWithGivenAnnotationsBuilder]]
      */
    // process access modifier
    // very rude mapping from flexible Scala access modifiers
    if (isField && modifiersList.isDefined) {
      javaModifiers += modifiersList.get.accessModifier
        .map {
          case e if e.isPrivate || e.isThis => PsiModifier.PRIVATE
          case e if e.isProtected           => PsiModifier.PROTECTED
          case _                            => PsiModifier.PUBLIC
        }
        .getOrElse(PsiModifier.PUBLIC)
    }

    // process immutability modifier
    if (isVal) javaModifiers += PsiModifier.FINAL

    new LightVariableWithGivenAnnotationsBuilder(
      name,
      psiType,
      containingTypeDef,
      modifiersList
        .map(_.getAnnotations)
        .getOrElse(Array.empty),
      javaModifiers.toArray
    )
  }

  /**
    * Tries to convert to [[UField]] from
    *  - [[ScReferencePattern]] representing "field" name id inside [[ScPatternDefinition]] declaration
    *  - [[ScClassParameter]] if it is class member
    *
    * @return functional trait which takes one lazy __nullable__ param representing
    *         optional parent of the field in the UAST
    */
  def unapply(arg: PsiElement): Option[Parent2ScUVariable] = {

    def buildUVariable(isField: Boolean) =
      if (isField) new ScUField(_, _, _, _, _, _)
      else new ScULocalVariable(_, _, _, _, _, _)

    arg match {
      case namePattern: ScReferencePattern =>
        for {
          declarationExpr <- Option(
            getParentOfType(namePattern, classOf[ScValueOrVariable])
          )
          containingTypeDef <- Option(
            getParentOfType(namePattern, classOf[ScTemplateDefinition])
          )
          variableType <- namePattern.`type`().toOption.map(_.toPsiType)
        } yield {
          val isField =
            Option(getParentOfType(namePattern, classOf[ScMethodLike]))
              .forall { containingMethod =>
                findCommonParent(containingTypeDef, containingMethod) == containingMethod
              }

          val lightVariable = createLightVariable(
            isField,
            namePattern.name,
            variableType,
            namePattern.isVal,
            containingTypeDef,
            Some(declarationExpr.getModifierList)
          )

          val initializer = declarationExpr match {
            case value: ScPatternDefinition     => value.expr
            case variable: ScVariableDefinition => variable.expr
            case _                              => None
          }

          buildUVariable(isField)(
            lightVariable,
            namePattern,
            namePattern.nameId,
            declarationExpr.typeElement,
            initializer,
            _
          )
        }

      case classParam: ScClassParameter if classParam.isClassMember =>
        for (fieldType <- classParam.`type`().toOption.map(_.toPsiType)) yield {
          val lightField = createLightVariable(
            isField = true,
            classParam.name,
            fieldType,
            isVal = classParam.isVal,
            classParam.containingClass,
            Some(classParam.getModifierList)
          )
          buildUVariable(isField = true)(
            lightField,
            classParam,
            classParam.nameId,
            classParam.typeElement,
            classParam.getDefaultExpression,
            _
          )
        }

      case _ => None
    }
  }

  trait Parent2ScUVariable {
    def apply(parent: LazyUElement): ScUVariableCommon
  }

}
