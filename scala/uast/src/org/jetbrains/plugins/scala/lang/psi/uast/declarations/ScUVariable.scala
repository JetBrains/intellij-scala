package org.jetbrains.plugins.scala.lang.psi.uast.declarations

import com.intellij.lang.Language
import com.intellij.psi.impl.light.{LightFieldBuilder, LightModifierList, LightTypeElement, LightVariableBuilder}
import com.intellij.psi.util.PsiTreeUtil._
import com.intellij.psi.{PsiAnnotation, PsiElement, PsiField, PsiFile, PsiLocalVariable, PsiManager, PsiModifier, PsiModifierList, PsiType, PsiTypeElement, PsiVariable}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScFieldId, ScModifierList}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockStatement, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScValue, ScValueOrVariable, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.ScUElement
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.uast._

import _root_.java.util
import scala.collection.mutable
import scala.jdk.CollectionConverters._

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
  trait Parent2ScUVariable {
    def apply(parent: LazyUElement): ScUVariableCommon
  }

  type TypeableScPsiElement = ScalaPsiElement with Typeable

  def unapply(arg: ScalaPsiElement): Option[Parent2ScUVariable] = {
    arg match {
      case classParam: ScClassParameter if classParam.isClassMember =>
        field(classParam)
      case fieldId: ScFieldId =>
        Option(getParentOfType(fieldId, classOf[ScValueOrVariable])).flatMap { declarationExpr =>
          field(fieldId,
            declarationExpr,
            fieldId,
            declarationExpr.typeElement,
            getVariableInitializer(declarationExpr),
            declarationExpr.is[ScValue])
        }
      case namePattern: ScReferencePattern =>
        Option(getParentOfType(namePattern, classOf[ScValueOrVariable])).flatMap { declarationExpr =>
          fieldOrLocalVariable(namePattern,
            declarationExpr,
            namePattern,
            declarationExpr.typeElement,
            getVariableInitializer(declarationExpr),
            declarationExpr.is[ScValue])
        }
      case _ => None
    }
  }

  private def getVariableInitializer(declarationExpr: ScValueOrVariable): Option[ScExpression] = declarationExpr match {
    case value: ScPatternDefinition => value.expr
    case variable: ScVariableDefinition => variable.expr
    case _ => None
  }

  private def fieldOrLocalVariable(sourcePsi: ScalaPsiElement,
                                   member: ScMember with Typeable,
                                   nameHolder: ScNamedElement,
                                   typeElement: Option[ScTypeElement],
                                   initializer: Option[ScExpression],
                                   isFinal: Boolean): Option[Parent2ScUVariable] = {
    val containingTypeDef = getParentOfType(member, classOf[ScTemplateDefinition])
    val isField = !member.isLocal && containingTypeDef != null

    if (isField) field(sourcePsi, member, nameHolder, typeElement, initializer, isFinal)
    else localVariable(sourcePsi, member, nameHolder, typeElement, initializer, isFinal)
  }

  private[uast] def createLightLocalVariable(name: String,
                                             containingFile: PsiFile,
                                             typeable: TypeableScPsiElement,
                                             modifierList: Option[ScModifierList],
                                             isFinal: Boolean,
                                             isField: Boolean): PsiLocalVariable =
    new ScAnnotatedLightLocalVariable(name, containingFile, typeable, modifierList, isFinal, isField)

  private def localVariable(sourcePsi: ScalaPsiElement,
                            element: ScMember with Typeable,
                            named: ScNamedElement,
                            typeElement: Option[ScTypeElement],
                            initializer: Option[ScExpression],
                            isFinal: Boolean): Option[Parent2ScUVariable] = {
    Option(element.getContainingFile).map { containingFile =>
      val lightLocalVariable = createLightLocalVariable(
        named.name,
        containingFile,
        element,
        modifierList = Some(element.getModifierList),
        isField = false,
        isFinal = isFinal
      )

      new ScULocalVariable(
        lightLocalVariable,
        sourcePsi,
        named.nameId,
        typeElement,
        initializer,
        _
      )
    }
  }

  private def field(classParam: ScClassParameter): Option[Parent2ScUVariable] =
    field(classParam, classParam, classParam, classParam.typeElement, classParam.getDefaultExpression, classParam.isVal)

  private def field(sourcePsi: ScalaPsiElement,
                    member: ScMember with Typeable,
                    nameHolder: ScNamedElement,
                    typeElement: Option[ScTypeElement],
                    initializer: Option[ScExpression],
                    isFinal: Boolean): Option[Parent2ScUVariable] = {
    Option(member.containingClass).collect { case containingClass: ScTypeDefinition =>
      val lightField: PsiField = new ScAnnotatedLightField(
        nameHolder.name,
        containingClass,
        member,
        Some(member.getModifierList),
        isFinal
      )

      new ScUField(
        lightField,
        sourcePsi,
        nameHolder.nameId,
        typeElement,
        initializer,
        _
      )
    }
  }

  private def getJavaModifiers(modifierList: Option[ScModifierList], isVal: Boolean, isField: Boolean): Seq[String] = {
    val javaModifiers = mutable.ArrayBuffer.empty[String]

    if (isField) modifierList.foreach { modifierList =>
      // access modifier
      // very rude mapping from flexible Scala access modifiers
      javaModifiers += modifierList.accessModifier
        .map {
          case e if e.isPrivate || e.isThis => PsiModifier.PRIVATE
          case e if e.isProtected => PsiModifier.PROTECTED
          case _ => PsiModifier.PUBLIC
        }
        .getOrElse(PsiModifier.PUBLIC)
    }

    // immutability modifier
    if (isVal) javaModifiers += PsiModifier.FINAL

    javaModifiers.toSeq
  }

  private class LightModifierListWithGivenAnnotations(manager: PsiManager,
                                                      lang: Language,
                                                      annotations: Array[PsiAnnotation],
                                                      modifiers: String*)
    extends LightModifierList(manager, lang, modifiers: _*) {

    override def getAnnotations: Array[PsiAnnotation] = annotations

    override def findAnnotation(qualifiedName: String): PsiAnnotation =
      annotations.find(_.getQualifiedName == qualifiedName).orNull
  }

  private trait ScAnnotatedLightVariable {
    self: LightVariableBuilder[_] =>
    @volatile protected var myModifierList: LightModifierList = _

    protected def annotations: Array[PsiAnnotation]

    protected def modifiers: Seq[String]

    locally {
      this.setModifiers(modifiers)
    }

    private def setModifiers(modifiers: Seq[String]): Unit =
      myModifierList = new LightModifierListWithGivenAnnotations(getManager, getLanguage, annotations, modifiers: _*)

    override def setModifiers(modifiers: String*): self.type = {
      setModifiers(modifiers)
      self
    }

    override def getModifierList: PsiModifierList = myModifierList

    override def hasModifierProperty(name: String): Boolean = myModifierList.hasModifierProperty(name)

    override def getTypeElement: PsiTypeElement = new LightTypeElement(getManager, getType)
  }

  private class ScAnnotatedLightField(name: String, containingClass: ScTypeDefinition,
                                      typeable: TypeableScPsiElement,
                                      override protected val annotations: Array[PsiAnnotation],
                                      override protected val modifiers: Seq[String])
    extends LightFieldBuilder(name, UastErrorType.INSTANCE, containingClass)
      with ScAnnotatedLightVariable {

    def this(name: String,
             containingClass: ScTypeDefinition,
             typeable: TypeableScPsiElement,
             modifierList: Option[ScModifierList],
             isFinal: Boolean) =
      this(name, containingClass, typeable, modifierList.map(_.getAnnotations).getOrElse(Array.empty),
        getJavaModifiers(modifierList, isVal = isFinal, isField = true))

    override lazy val getType: PsiType =
      if (typeable.isValid) typeable.`type`() match {
        case Right(scType) => scType.toPsiType
        case Left(_) => super.getType
      } else super.getType
  }

  private class ScAnnotatedLightLocalVariable(name: String,
                                              containingFile: PsiFile,
                                              typeable: TypeableScPsiElement,
                                              override protected val annotations: Array[PsiAnnotation],
                                              override protected val modifiers: Seq[String])
    extends LightVariableBuilder[ScAnnotatedLightLocalVariable](containingFile.getManager, name, UastErrorType.INSTANCE, containingFile.getLanguage)
      with PsiLocalVariable
      with ScAnnotatedLightVariable {

    def this(name: String,
             containingFile: PsiFile,
             typeable: TypeableScPsiElement,
             modifierList: Option[ScModifierList],
             isFinal: Boolean,
             isField: Boolean) =
      this(name, containingFile, typeable, modifierList.map(_.getAnnotations).getOrElse(Array.empty),
        getJavaModifiers(modifierList, isVal = isFinal, isField = isField))

    override lazy val getType: PsiType =
      if (typeable.isValid) typeable.`type`() match {
        case Right(scType) => scType.toPsiType
        case Left(_) => super.getType
      } else super.getType
  }
}
