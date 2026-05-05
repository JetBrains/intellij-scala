package org.jetbrains.plugins.scala.annotator.element

import com.intellij.lang.annotation.AnnotationSession
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.annotator.annotationHolder.DelegateAnnotationHolder
import org.jetbrains.plugins.scala.annotator.template.ImplicitParametersAnnotator
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScDerivesClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.DerivesUtil.synthesizeDerivedGiven

import scala.annotation.nowarn

object ScDerivesClauseAnnotator extends ElementAnnotator[ScDerivesClause] {
  private def annotateSyntheticDerivedMembers(
    ref:                         ScReference,
    syntheticDerivedMemberText:  String,
    companion:                   ScObject,
    session:                     AnnotationSession,
    typeAware:                   Boolean
  )(implicit
    holder: ScalaAnnotationHolder
  ): Unit = {
    val delegateHolder = new DelegateAnnotationHolder(session) {
      override protected def element: Option[PsiElement] = Option(ref)
      override protected def transformRange(range: TextRange): TextRange = ref.getTextRange
    }

    val syntheticMemberContext =
      if (companion.isSyntheticObject) ScalaPsiUtil.getCompanionModule(companion).getOrElse(companion)
      else                             companion

    val derivedMember = ScalaPsiElementFactory
      .safe(_.createMethodWithContext(syntheticDerivedMemberText, syntheticMemberContext, companion))
      .collect { case fdef: ScFunctionDefinition =>
        fdef.syntheticNavigationElement = syntheticMemberContext
        fdef.syntheticContainingClass = companion
        fdef
      }

    derivedMember.foreach { member =>
      member.body.foreach { body =>
        ImplicitParametersAnnotator.annotate(body, typeAware)(delegateHolder)
        ScExpressionAnnotator.checkExpressionType(body, typeAware, inDesugaring = true)(delegateHolder)
      }
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
      synthesizeDerivedGiven(ref, owner, shouldValidateDerivedMethod = true) match {
        case Right(syntheticDerivedMemberText) =>
          companion.foreach(obj => annotateSyntheticDerivedMembers(ref, syntheticDerivedMemberText, obj, session, typeAware))
        case Left(error) =>
          holder.createErrorAnnotation(ref, error)
      }
    }
  }
}
