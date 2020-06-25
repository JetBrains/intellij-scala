package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.psi.{PsiAnnotation, PsiClass}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.adapters.PsiAnnotatedAdapter
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createAnAnnotation, createNewLine}
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{AliasType, ScType}
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

import scala.annotation.tailrec

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.01.2009
 */

trait ScAnnotationsHolder extends ScalaPsiElement with PsiAnnotatedAdapter {

  @Cached(ModCount.anyScalaPsiModificationCount, this)
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

  override def addAnnotation(qualifiedName: String): PsiAnnotation = {
    val container = findChildByClassScala(classOf[ScAnnotations])

    val added = container.add(createAnAnnotation(qualifiedName))
    container.add(createNewLine())

    ScalaPsiUtil.adjustTypes(added, addImports = true)
    added.asInstanceOf[PsiAnnotation]
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

  override def getApplicableAnnotations: Array[PsiAnnotation] = getAnnotations //todo: understatnd and fix

  override def psiAnnotations: Array[PsiAnnotation] = annotations.toArray
}