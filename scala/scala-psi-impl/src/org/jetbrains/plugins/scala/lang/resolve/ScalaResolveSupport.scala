package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiReference
import org.jetbrains.annotations.{ApiStatus, Nullable}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScEnd, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScEnumerator, ScSelfInvocation}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScAccessModifierImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScSuperReferenceImpl
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScPsiDocToken

/**
 * It's registered only in the backend module scalaCommunity.scala-impl. On the frontend of Remote Development the
 * shared PSI is loaded too, but the platform must not resolve Scala references there (see IJPL-A-1220
 * "Moving PSI Components", "Keep References on Backend"): the frontend has no indexes and no complete project model.
 *
 * @see [[EmptyScalaResolveSupport]]
 * @see [[BackendScalaResolveSupport]]
 */
@ApiStatus.Internal
trait ScalaResolveSupport {
  @Nullable
  def getReference(accessModifier: ScAccessModifierImpl): PsiReference

  @Nullable
  def getReference(end: ScEnd): ScEnd

  @Nullable
  def getReference(enumerator: ScEnumerator): ScEnumerator

  @Nullable
  def getReference(reference: ScReference): ScReference

  @Nullable
  def getReference(selfInvocation: ScSelfInvocation): ScSelfInvocation

  @Nullable
  def getReference(superReference: ScSuperReferenceImpl): PsiReference

  def getReferences(docToken: ScPsiDocToken): Array[PsiReference]

  def getReferences(referencePattern: ScReferencePattern): Array[PsiReference]
}

object ScalaResolveSupport {
  def getInstance: ScalaResolveSupport = {
    val instance = getInstanceOrNull
    if (instance != null) instance else EmptyScalaResolveSupport
  }

  def isAvailable: Boolean = getInstanceOrNull != null

  @Nullable
  private def getInstanceOrNull: ScalaResolveSupport = ApplicationManager.getApplication match {
    case null => null
    case application => application.getService(classOf[ScalaResolveSupport])
  }
}
