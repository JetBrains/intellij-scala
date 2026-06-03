package org.jetbrains.plugins.scala.lang.psi.uast.declarations

import com.intellij.openapi.util.{Key, UserDataHolderBase}
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParameterizedTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScConstructorOwner, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{ScUAnchorOwner, ScUAnnotated, ScUElement}
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.plugins.scala.lang.psi.uast.psi.PsiClassAnonymousWrapper

import java.{util => ju}
import org.jetbrains.uast._

import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._

trait ScUClassCommon extends UClass with ScUAnnotated {

  @Nullable
  override def getJavaPsi: PsiClass = null

  protected val scTemplate: ScTemplateDefinition

  override def getUastDeclarations: ju.List[UDeclaration] =
    Seq.concat[UDeclaration](getUFields, getUInitializers, getUMethods, getUInnerClasses).asJava

  override def getUastSuperTypes: ju.List[UTypeReferenceExpression] = {
    val templateParents = scTemplate.extendsBlock.templateParents
    val superReferences = templateParents.map(uastSuperTypesReferences)
    superReferences.map(_.asJava).getOrElse(ju.Collections.emptyList())
  }

  private def uastSuperTypesReferences(templateParents: ScTemplateParents): Seq[UTypeReferenceExpression] = {
    val typeElements = templateParents.typeElements
    typeElements.flatMap { te =>
      val referenceTypeElement = te match {
        //in case we have type representing `BaseClass[TypeArg]` we need to get `BaseClass`
        case pte: ScParameterizedTypeElement => pte.typeElement
        case _ => te
      }
      referenceTypeElement.convertTo[UTypeReferenceExpression](this)
    }
  }

  def getUFields: Array[UField] = {
    val uFields = ArrayBuffer.empty[UField]

    scTemplate.members.foreach {
      case valOrVar: ScValueOrVariable =>
        for {
          elem <- valOrVar.declaredElements
          refPattern <- Option(elem.nameId.getParent)
          uFieldFromValOrVarClause <- refPattern.convertTo[UField](this)
        } uFields += uFieldFromValOrVarClause

      case _ =>
    }

    scTemplate match {
      case scClass: ScClass =>
        scClass.parameters.iterator.flatMap(_.convertTo[UField](this)).foreach {
          uFieldFromClassParameter =>
            uFields += uFieldFromClassParameter
        }
      case _ =>
    }

    uFields.toArray
  }

  def getUInitializers: Array[UClassInitializer] =
    Array.empty // TODO: not implemented

  def getUMethods: Array[UMethod] = {
    val primaryConstructor = scTemplate.asOptionOf[ScConstructorOwner].flatMap(_.constructor)

    (primaryConstructor ++ scTemplate.functions)
      .flatMap(_.convertTo[UMethod](this))
      .toArray
  }

  def getUInnerClasses: Array[UClass] =
    scTemplate.typeDefinitions
      .flatMap(_.convertTo[UClass](this))
      .toArray

  abstract override def asRenderString(): String = {
    val uAnnotations = getUAnnotations
    val renderedAnnotations =
      if (!uAnnotations.isEmpty)
        uAnnotations.asScala
          .map(_.asRenderString())
          .mkString(start = "", sep = "\n", end = "\n")
      else
        ""

    renderedAnnotations +
      super.asRenderString()
  }
}

/**
 * [[ScTypeDefinition]] adapter for the [[UClass]]
 *
 * @param scElement Scala PSI element representing type definition
 *                  (e.g. class, object, trait)
 */
final class ScUClass(override protected val scElement: ScTypeDefinition,
                     override protected val parent: LazyUElement)
  extends UClassAdapter(scElement)
    with ScUClassCommon
    with UClass
    with ScUElement
    with ScUAnchorOwner {

  override type PsiFacade = PsiClass
  override protected val scTemplate: ScTemplateDefinition = scElement
  override protected val namedElement: ScNamedElement = scElement

  override def getUFields: Array[UField] = super[ScUClassCommon].getUFields
  override def getUMethods: Array[UMethod] = super[ScUClassCommon].getUMethods
  override def getUInitializers: Array[UClassInitializer] =
    super[ScUClassCommon].getUInitializers
  override def getUInnerClasses: Array[UClass] =
    super[ScUClassCommon].getUInnerClasses
}

/**
 * [[ScNewTemplateDefinition]] adapter for the [[UClass]]
 *
 * @param newTemplateDefinition Scala PSI expression representing anonymous class
 *                              definition
 */
final class ScUAnonymousClass(
  newTemplateDefinition: ScNewTemplateDefinition,
  extendsBlock: ScExtendsBlock,
  override protected val parent: LazyUElement
) extends UClassAdapter(newTemplateDefinition)
  with ScUClassCommon
  with UClass
  with ScUElement
  with UAnonymousClass
  with PsiAnonymousClass {

  override protected val scTemplate: ScTemplateDefinition = newTemplateDefinition

  override def getUFields: Array[UField] = super[ScUClassCommon].getUFields
  override def getUMethods: Array[UMethod] = super[ScUClassCommon].getUMethods
  override def getUInitializers: Array[UClassInitializer] =
    super[ScUClassCommon].getUInitializers
  override def getUInnerClasses: Array[UClass] =
    super[ScUClassCommon].getUInnerClasses

  override type PsiFacade = PsiAnonymousClass
  override protected val scElement: PsiFacade = new ScTemplateToPsiAnonymousClassAdapter(newTemplateDefinition)

  @Nullable
  override def getSourcePsi: PsiElement = extendsBlock

  @Nullable
  override def getUastAnchor: UElement =
    extendsBlock.templateParents
      .flatMap(_.firstParentClause)
      .map(createUIdentifier(_, this))
      .getOrElse(createUIdentifier(extendsBlock, this))

  override def getBaseClassReference: PsiJavaCodeReferenceElement =
    JavaPsiFacade
      .getElementFactory(newTemplateDefinition.getProject)
      .createReferenceElementByType(getBaseClassType)

  override def getBaseClassType: PsiClassType =
    extendsBlock.templateParents
      .flatMap(
        _.typeElements
          .flatMap(
            _.`type`().toOption.map(
              scType =>
                PsiType.getTypeByName(
                  scType.canonicalText,
                  newTemplateDefinition.getProject,
                  newTemplateDefinition.getResolveScope
                )
            )
          )
          .headOption
      )
      .getOrElse(
        PsiType.getJavaLangObject(
          PsiManager.getInstance(newTemplateDefinition.getProject),
          GlobalSearchScope.allScope(newTemplateDefinition.getProject)
        )
      )

  @Nullable
  override def getArgumentList: PsiExpressionList = null

  override def isInQualifiedNew: Boolean =
    extendsBlock.templateParents
      .flatMap(_.firstParentClause)
      .flatMap(_.reference)
      .exists(_.qualifier.isDefined)

  // escapes looping caused by the default implementation
  @Nullable
  override def getSuperClass: UClass =
    Option(newTemplateDefinition.getSuperClass)
      .flatMap(_.convertWithParentTo[UClass]())
      .orNull
}

private final class ScTemplateToPsiAnonymousClassAdapter(scTemplate: ScNewTemplateDefinition) extends PsiClassAnonymousWrapper {
  override protected def getDelegate: PsiClass = scTemplate

  override def getBaseClassReference: PsiJavaCodeReferenceElement =
    JavaPsiFacade.getElementFactory(getProject).createReferenceElementByType(getBaseClassType)

  override def getBaseClassType: PsiClassType =
    scTemplate.firstConstructorInvocation.flatMap(_.reference)
      .flatMap(_.resolve().asOptionOf[PsiClass])
      .map(PsiTypesUtil.getClassType)
      .getOrElse(PsiType.getJavaLangObject(scTemplate.getManager, scTemplate.getResolveScope))

  override def getArgumentList: PsiExpressionList = null

  override def isInQualifiedNew: Boolean = false

  private val dataHolder = new UserDataHolderBase

  override def getCopyableUserData[T](key: Key[T]): T = dataHolder.getCopyableUserData(key)

  override def putCopyableUserData[T](key: Key[T], value: T): Unit = dataHolder.putCopyableUserData(key, value)

  override def getUserData[T](key: Key[T]): T = dataHolder.getUserData(key)

  override def putUserData[T](key: Key[T], value: T): Unit = dataHolder.putUserData(key, value)
}


/**
 * Mock of the [[UClass]] when conversion failed
 *
 * @param scElement Scala PSI element implementing [[PsiClass]]
 */
final class ScUErrorClass(override protected val scElement: PsiClass,
                          override protected val parent: LazyUElement)
  extends UClassAdapter(scElement)
    with ScUElement with ScUAnnotated {

  override type PsiFacade = PsiClass

  @Nullable
  override def getJavaPsi: PsiClass = null

  @Nullable
  override def getPsi: PsiClass = null

  override def getUFields: Array[UField] = Array.empty

  override def getUMethods: Array[UMethod] = Array.empty

  override def getUInitializers: Array[UClassInitializer] = Array.empty

  override def getUInnerClasses: Array[UClass] = Array.empty

  override def getUastDeclarations: ju.List[UDeclaration] =
    ju.Collections.emptyList()

  override def getUastSuperTypes: ju.List[UTypeReferenceExpression] =
    ju.Collections.emptyList()

  @Nullable
  override def getUastAnchor: UElement = createUIdentifier(scElement, this)
}
