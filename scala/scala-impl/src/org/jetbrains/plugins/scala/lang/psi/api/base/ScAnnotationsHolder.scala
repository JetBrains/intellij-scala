package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.psi.{PsiAnnotation, PsiClass}
import org.jetbrains.plugins.scala.caches.ModTracker
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.adapters.PsiAnnotatedAdapter
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createAnAnnotation, createNewLine}
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{AliasType, ScType}
import org.jetbrains.plugins.scala.macroAnnotations.Cached

import scala.annotation.tailrec

trait ScAnnotationsHolder extends ScalaPsiElement with PsiAnnotatedAdapter {

  @Cached(ModTracker.anyScalaPsiChange, this)
  def annotations: Seq[ScAnnotation] = this.stubOrPsiChild(ScalaElementType.ANNOTATIONS) match {
    case Some(ann) => ann.getAnnotations.toSeq
    case _ => Seq.empty
  }

  override def hasAnnotation(qualifiedName: String): Boolean = annotations(qualifiedName).nonEmpty

  def annotations(qualifiedName: String): Seq[ScAnnotation] = {
    @tailrec
    def acceptType(scType: ScType): Boolean = scType match {
      case ScDesignatorType(clazz: PsiClass) =>
        clazz.qualifiedName == qualifiedName
      case ParameterizedType(designator@ScDesignatorType(_: PsiClass), _) =>
        acceptType(designator)
      case ScProjectionType(_, clazz: PsiClass) =>
        clazz.qualifiedName == qualifiedName
      case tp =>
        tp match {
          case AliasType(definition: ScTypeAliasDefinition, _, _) =>
            acceptType(definition.aliasedType.getOrAny)
          case _ => false
        }
    }

    def checkTypeName(annotation: ScAnnotation): Boolean = acceptType(annotation.typeElement.`type`().getOrAny)

    annotations.filter(checkTypeName)
  }

  override def addAnnotation(qualifiedName: String): PsiAnnotation = addAnnotation(qualifiedName, addNewLine = true)

  def addAnnotation(qualifiedName: String, addNewLine: Boolean): ScAnnotation = {
    val container = findChild[ScAnnotations].get

    val added = container.add(createAnAnnotation(qualifiedName))
    if (addNewLine) container.add(createNewLine())

    ScalaPsiUtil.adjustTypes(added)
    added.asInstanceOf[ScAnnotation]
  }

  override def findAnnotation(qualifiedName: String): PsiAnnotation =
    annotations(qualifiedName).headOption.orNull

  def findAnnotationNoAliases(qualifiedName: String): PsiAnnotation = {
    @tailrec
    def sameName(te: ScTypeElement): Boolean = te match {
      case simple: ScSimpleTypeElement =>
        simple.reference exists {
          _.refName == qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1)
        }
      case ScParameterizedTypeElement(simple: ScSimpleTypeElement, _) => sameName(simple)
      case _ => false
    }
    def hasSameName(annotation: ScAnnotation) = sameName(annotation.typeElement)

    annotations.find(hasSameName).orNull
  }

  /**
   * @inheritdoc
   *
   * TODO: understand and fix
   *  for Java annotations implementations can be probably copied from<br>
   *  [[com.intellij.psi.impl.source.PsiModifierListImpl.getApplicableAnnotations]]<br>
   *  but for Scala annotaions "targets" are not defined the same way as for Java (com.intellij.psi.PsiAnnotation.TargetType)
   *
   *  See fore example definition of [[scala.deprecatedInheritance]] or [[scala.annotation.compileTimeOnly]]
   */
  override def getApplicableAnnotations: Array[PsiAnnotation] = getAnnotations

  override final def psiAnnotations: Array[PsiAnnotation] = annotations.toArray
}