package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAnnotation, ScAnnotations}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createAnAnnotation, createNewLine}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType
import org.jetbrains.plugins.scala.macroAnnotations._

import scala.meta.intellij.MetaExpansionsManager

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.01.2009
 */

trait ScAnnotationsHolder extends ScalaPsiElement with PsiAnnotationOwner {
  def annotations: Seq[ScAnnotation] = this.stubOrPsiChild(ScalaElementTypes.ANNOTATIONS) match {
    case Some(ann) => ann.getAnnotations.toSeq
    case _ => Seq.empty
  }

  def hasAnnotation(qualifiedName: String): Boolean = annotations(qualifiedName).nonEmpty

  def annotations(qualifiedName: String): Seq[ScAnnotation] = {
    def acceptType: ScType => Boolean = {
      case ScDesignatorType(clazz: PsiClass) =>
        clazz.qualifiedName == qualifiedName
      case ParameterizedType(designator@ScDesignatorType(_: PsiClass), _) =>
        acceptType(designator)
      case tp =>
        tp.isAliasType collect {
          case AliasType(definition: ScTypeAliasDefinition, _, _) =>
            definition.aliasedType.getOrAny
        } exists {
          acceptType
        }
    }

    annotations map { annotation =>
      (annotation, annotation.typeElement.getType().getOrAny)
    } filter {
      case (_, scType) => acceptType(scType)
    } map {
      _._1
    }
  }

  def addAnnotation(qualifiedName: String): PsiAnnotation = {
    val container = findChildByClassScala(classOf[ScAnnotations])

    val added = container.add(createAnAnnotation(qualifiedName))
    container.add(createNewLine())

    ScalaPsiUtil.adjustTypes(added, addImports = true)
    added.asInstanceOf[PsiAnnotation]
  }

  def findAnnotation(qualifiedName: String): PsiAnnotation =
    annotations(qualifiedName).headOption.orNull

  def findAnnotationNoAliases(qualifiedName: String): PsiAnnotation = {
    def sameName: ScTypeElement => Boolean = {
      case simple: ScSimpleTypeElement =>
        simple.reference exists {
          _.refName == qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1)
        }
      case ScParameterizedTypeElement(simple: ScSimpleTypeElement, _) => sameName(simple)
      case _ => false
    }

    annotations map {
      _.typeElement
    } find {
      sameName
    } map { _ =>
      findAnnotation(qualifiedName)
    } orNull
  }

  @CachedWithRecursionGuard(this, Left("Recursive meta expansion"), ModCount.getBlockModificationCount)
  def getMetaExpansion: Either[String, scala.meta.Tree] = {
    val metaAnnotation = annotations.find(_.isMetaAnnotation)
    metaAnnotation match {
      case Some(annot) => MetaExpansionsManager.runMetaAnnotation(annot)
      case None        => Left("")
    }
  }

  def getApplicableAnnotations: Array[PsiAnnotation] = getAnnotations //todo: understatnd and fix

  def getAnnotations: Array[PsiAnnotation] = annotations.toArray
}