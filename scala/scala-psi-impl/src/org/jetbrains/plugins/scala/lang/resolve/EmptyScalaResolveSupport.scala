package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.PsiReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScEnd, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScEnumerator, ScSelfInvocation}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScAccessModifierImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScSuperReferenceImpl
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScPsiDocToken

object EmptyScalaResolveSupport extends ScalaResolveSupport {
  override def getReference(accessModifier: ScAccessModifierImpl): PsiReference = null

  override def getReference(end: ScEnd): ScEnd = null

  override def getReference(enumerator: ScEnumerator): ScEnumerator = null

  override def getReference(reference: ScReference): ScReference = null

  override def getReference(selfInvocation: ScSelfInvocation): ScSelfInvocation = null

  override def getReference(superReference: ScSuperReferenceImpl): PsiReference = null

  override def getReferences(docToken: ScPsiDocToken): Array[PsiReference] = PsiReference.EMPTY_ARRAY

  override def getReferences(referencePattern: ScReferencePattern): Array[PsiReference] = PsiReference.EMPTY_ARRAY
}
