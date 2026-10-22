package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.{PsiReference, PsiReferenceService}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScEnd, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScEnumerator, ScSelfInvocation}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScAccessModifierImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScSuperReferenceImpl
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScPsiDocToken

final class BackendScalaResolveSupport extends ScalaResolveSupport {
  override def getReference(accessModifier: ScAccessModifierImpl): PsiReference =
    accessModifier.idText.map(new accessModifier.AccessModifierReference(_)).orNull

  override def getReference(end: ScEnd): ScEnd = end

  override def getReference(enumerator: ScEnumerator): ScEnumerator = enumerator

  override def getReference(reference: ScReference): ScReference = reference

  override def getReference(selfInvocation: ScSelfInvocation): ScSelfInvocation = selfInvocation

  override def getReference(superReference: ScSuperReferenceImpl): PsiReference =
    superReference.identifier.map(new superReference.SuperReference(_)).orNull

  override def getReferences(docToken: ScPsiDocToken): Array[PsiReference] =
    ReferenceProvidersRegistry.getReferencesFromProviders(docToken)

  override def getReferences(referencePattern: ScReferencePattern): Array[PsiReference] =
    PsiReferenceService.getService.getContributedReferences(referencePattern)
}
