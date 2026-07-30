package org.jetbrains.plugins.scala.lang.psi.impl.search

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.{PsiField, PsiMember, PsiModifier}
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotations
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.light.ScLightField
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys

import scala.collection.mutable

class ScalaAnnotatedMembersSearcher extends QueryExecutor[PsiMember, AnnotatedElementsSearch.Parameters] {

  private def convertToLightField(fieldLike: ScValueOrVariable): Option[PsiField] =
    for {
      anchor <- fieldLike.declaredElements.headOption
      containingTypeDef <- Option(fieldLike.containingClass).collect { case c: ScTypeDefinition => c }
    } yield {
      val javaModifiers = mutable.ArrayBuffer.empty[String]
      // access modifier
      // very rude mapping from flexible Scala access modifiers
      javaModifiers += fieldLike.getModifierList.accessModifier
          .map {
            case e if e.isPrivate || e.isThis => PsiModifier.PRIVATE
            case e if e.isProtected           => PsiModifier.PROTECTED
            case _                            => PsiModifier.PUBLIC
          }
          .getOrElse(PsiModifier.PUBLIC)

      // immutability modifier
      if (anchor.isVal)
        javaModifiers += PsiModifier.FINAL

      ScLightField(
        anchor.name,
        anchor.`type`().getOrAny,
        containingTypeDef,
        javaModifiers.toSeq: _*
      )
    }

  override def execute(p: AnnotatedElementsSearch.Parameters, consumer: Processor[_ >: PsiMember]): Boolean = {
    val annotationFQN = getAnnotationName(p)
    assert(annotationFQN != null, "Annotation qualifier can't be null")

    inReadAction {
      executeInner(p, consumer, annotationFQN)
    }
  }

  @Nullable
  private def getAnnotationName(parameters: AnnotatedElementsSearch.Parameters): String = {
    val annotationName = parameters.getAnnotationName
    if (annotationName != null) annotationName
    else ReadAction.computeBlocking { () =>
      val annotationClass = parameters.getAnnotationClass
      if (annotationClass == null) null
      else {
        assert(annotationClass.annotationType, "Annotation type should be passed to annotated members search")
        annotationClass.qualifiedName
      }
    }
  }

  private def executeInner(
    parameters: AnnotatedElementsSearch.Parameters,
    consumer: Processor[_ >: PsiMember],
    annotationFQN: String,
  ): Boolean = {
    val scope = parameters.getScope match {
      case searchScope: GlobalSearchScope =>
        searchScope
      case _ =>
        return true
    }

    import ScalaIndexKeys._
    val shortName = StringUtil.getShortName(annotationFQN)
    val elements = ANNOTATED_MEMBER_KEY.elements(shortName, scope)(parameters.getProject)
    val iterator = elements.iterator
    while (iterator.hasNext) {
      val annotation = iterator.next()
      annotation.getParent match {
        case ann: ScAnnotations =>
          val memberOpt = Option(ann.getParent)
            .collect {
              case fieldLike: ScValueOrVariable =>
                convertToLightField(fieldLike).getOrElse(fieldLike)
              case member: PsiMember =>
                member
            }

          //noinspection ConvertibleToMethodValue
          val continue = memberOpt.forall(consumer.process(_))
          if (!continue)
            return false
        case _ =>
      }
    }

    true
  }
}
