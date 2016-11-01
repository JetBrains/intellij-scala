package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import java.io.IOException

import com.intellij.psi._
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.macros.expansion.MacroExpandAction
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAnnotation, ScAnnotations}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createAnAnnotation, createNewLine}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInsidePsiElement, CachedMacroUtil, ModCount}

import scala.meta.intellij.ExpansionUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.01.2009
 */

trait ScAnnotationsHolder extends ScalaPsiElement with PsiAnnotationOwner {
  def annotations: Seq[ScAnnotation] = {
    val maybeStub: Option[StubElement[_ <: PsiElement]] = Some(this) flatMap {
      case element: StubBasedPsiElement[_] =>
        // !!! Appeasing an unexplained compile error
        Option(element.getStub.asInstanceOf[StubElement[_ <: PsiElement]])
      case file: PsiFileImpl =>
        Option(file.getStub)
      case _ => None
    }

    val maybeStubAnnotations = maybeStub.toSeq.flatMap({
          _.getChildrenByType(TokenSet.create(ScalaElementTypes.ANNOTATIONS),
            JavaArrayFactoryUtil.ScAnnotationsFactory).toSeq
        }).headOption

    val maybeAnnotations = maybeStubAnnotations.orElse(Option(findChildByClassScala(classOf[ScAnnotations])))

    maybeAnnotations.toSeq.flatMap {
      _.getAnnotations.toSeq
    }
  }

  def hasAnnotation(qualifiedName: String): Boolean =
    annotations(qualifiedName).nonEmpty

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

  @CachedInsidePsiElement(this, ModCount.getBlockModificationCount)
  def getExpansionText: Either[String, String] = {
    val metaAnnotation = annotations.find(_.isMetaAnnotation)
    metaAnnotation match {
      case Some(annot) => ExpansionUtil.runMetaAnnotation(annot).right.map(_.toString())
      case None        => Right("")
    }
  }

  def getApplicableAnnotations: Array[PsiAnnotation] = getAnnotations //todo: understatnd and fix

  def getAnnotations: Array[PsiAnnotation] = annotations.toArray
}