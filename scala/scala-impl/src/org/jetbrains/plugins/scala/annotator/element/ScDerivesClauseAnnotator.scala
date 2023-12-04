package org.jetbrains.plugins.scala.annotator.element

import com.intellij.lang.annotation.AnnotationSession
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.annotator.annotationHolder.DelegateAnnotationHolder
import org.jetbrains.plugins.scala.annotator.template.ImplicitParametersAnnotator
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScDerivesClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.DerivesUtil.{checkIfCanBeDerived, resolveTypeClassReference}

import scala.annotation.nowarn

object ScDerivesClauseAnnotator extends ElementAnnotator[ScDerivesClause] {
  private def annotateSyntheticDerivedMembers(
    ref:       ScReference,
    companion: ScObject,
    session:   AnnotationSession,
    typeAware: Boolean
  )(implicit
    holder: ScalaAnnotationHolder
  ): Unit = {
    val refName = ref.refName

    val delegateHolder = new DelegateAnnotationHolder(session) {
      override protected def element: Option[PsiElement] = Option(ref)
      override protected def transformRange(range: TextRange): TextRange = ref.getTextRange
    }

    val derivedMember = companion.syntheticMethods.collect {
      case fdef: ScFunctionDefinition if fdef.name == s"derived$$$refName" => fdef
    }

    for {
      member <- derivedMember
      body   <- member.body
    } {
      ImplicitParametersAnnotator.annotate(body, typeAware)(delegateHolder)
      ScExpressionAnnotator.checkExpressionType(body, typeAware, inDesugaring = true)(delegateHolder)
    }
  }

  override def annotate(
    element:   ScDerivesClause,
    typeAware: Boolean
  )(implicit
    holder: ScalaAnnotationHolder
  ): Unit = {
    if (!typeAware) return

    val owner     = element.owner
    val session   = new AnnotationSession(element.getContainingFile): @nowarn("cat=deprecation")
    val companion = owner.baseCompanion.collect { case obj: ScObject => obj }.orElse(owner.fakeCompanionModule)

    element.derivedReferences.foreach { ref =>
      val eitherTcOrError = resolveTypeClassReference(ref)

      eitherTcOrError match {
        case Right(tc) =>
          val derivationCheck = checkIfCanBeDerived(tc, ref.refName, owner)
          derivationCheck match {
            case Right(_) =>
              companion.foreach(obj => annotateSyntheticDerivedMembers(ref, obj, session, typeAware))
            case Left(error) => holder.createErrorAnnotation(ref, error)
          }
        case Left(error) => holder.createErrorAnnotation(ref, error)
      }
    }
  }
}
