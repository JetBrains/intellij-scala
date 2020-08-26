package org.jetbrains.plugins.scala.lang.psi.uast.declarations

import java.{util => ju}

import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{ScUAnchorOwner, ScUAnnotated, ScUElement}
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.uast._

import scala.jdk.CollectionConverters._
import scala.collection.mutable.ArrayBuffer

trait ScUClassCommon extends UClass with ScUAnnotated {

  protected val scTemplate: ScTemplateDefinition

  override def getUastDeclarations: ju.List[UDeclaration] =
    Seq.concat[UDeclaration](getUFields, getUInitializers, getUMethods, getUInnerClasses).asJava

  override def getUastSuperTypes: ju.List[UTypeReferenceExpression] =
    scTemplate.extendsBlock.templateParents
      .map(
        _.typeElements
          .flatMap(_.convertTo[UTypeReferenceExpression](this))
          .asJava
      )
      .getOrElse(ju.Collections.emptyList())

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

  def getUMethods: Array[UMethod] =
    scTemplate.functions // .getMethods
      .filterNot(_.isConstructor)
      .flatMap(_.convertTo[UMethod](this))
      .toArray

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
  * @param scAnonDefinition Scala PSI expression representing anonymous class
  *                         definition
  */
final class ScUAnonymousClass(scAnonDefinition: ScNewTemplateDefinition,
                              override protected val parent: LazyUElement)
    extends UClassAdapter(scAnonDefinition)
    with ScUClassCommon
    with ScUElement
    with UAnonymousClass {

  override protected val scTemplate: ScTemplateDefinition = scAnonDefinition

  override def getUFields: Array[UField] = super[ScUClassCommon].getUFields
  override def getUMethods: Array[UMethod] = super[ScUClassCommon].getUMethods
  override def getUInitializers: Array[UClassInitializer] =
    super[ScUClassCommon].getUInitializers
  override def getUInnerClasses: Array[UClass] =
    super[ScUClassCommon].getUInnerClasses

  override type PsiFacade = PsiAnonymousClass
  override protected val scElement: PsiFacade = this

  @Nullable
  override def getSourcePsi: PsiElement =
    scAnonDefinition.extendsBlock.templateBody.orNull

  @Nullable
  override def getUastAnchor: UElement =
    scAnonDefinition.extendsBlock.templateParents
      .flatMap(_.constructorInvocation)
      .map(createUIdentifier(_, this))
      .getOrElse(createUIdentifier(scAnonDefinition.extendsBlock, this))

  override def getBaseClassReference: PsiJavaCodeReferenceElement =
    JavaPsiFacade
      .getElementFactory(scAnonDefinition.getProject)
      .createReferenceElementByType(getBaseClassType)

  override def getBaseClassType: PsiClassType =
    scAnonDefinition.extendsBlock.templateParents
      .flatMap(
        _.typeElements
          .flatMap(
            _.`type`().toOption.map(
              scType =>
                PsiType.getTypeByName(
                  scType.canonicalText,
                  scAnonDefinition.getProject,
                  scAnonDefinition.getResolveScope
              )
            )
          )
          .headOption
      )
      .getOrElse(
        PsiType.getJavaLangObject(
          PsiManager.getInstance(scAnonDefinition.getProject),
          GlobalSearchScope.allScope(scAnonDefinition.getProject)
        )
      )

  @Nullable
  override def getArgumentList: PsiExpressionList = null

  override def isInQualifiedNew: Boolean =
    scAnonDefinition.extendsBlock.templateParents
      .flatMap(_.constructorInvocation)
      .flatMap(_.reference)
      .exists(_.qualifier.isDefined)

  // escapes looping caused by the default implementation
  @Nullable
  override def getSuperClass: UClass =
    Option(scAnonDefinition.getSuperClass)
      .flatMap(_.convertWithParentTo[UClass]())
      .orNull
}

/**
  * Mock of the [[UClass]] when conversion failed
  *
  * @param scElement Scala PSI element implementing [[PsiClass]]
  */
final class ScUErrorClass(override protected val scElement: PsiClass,
                          override protected val parent: LazyUElement)
    extends UClassAdapter(scElement)
    with ScUElement {

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
