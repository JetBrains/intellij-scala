package org.jetbrains.plugins.scala
package lang
package psi
package api

import com.intellij.psi.{PsiElement, ResolveState}
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtension
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt

trait ScPackageLike extends PsiElement {

  def findPackageObject(scope: GlobalSearchScope): Option[ScObject]

  def parentScalaPackage: Option[ScPackageLike]

  def fqn: String

  def processTopLevelDeclarations(
    processor: PsiScopeProcessor,
    state:     ResolveState,
    place:     PsiElement
  ): Boolean = {
    getTopLevelDefs(place.resolveScope).forall {
      case ext: ScExtension => ext.extensionMethods.forall(processor.execute(_, state))
      case topLevelDef      => processor.execute(topLevelDef, state)
    }
  }

  private def getTopLevelDefs(scope: GlobalSearchScope): Iterable[ScMember] =
    ScalaPsiManager.instance(getProject).getTopLevelDefinitionsByPackage(fqn, scope)
}

object ScPackageLike {
  private[psi] def processPackageObject(
    `object`:   ScObject
  )(processor:  PsiScopeProcessor,
    state:      ResolveState,
    lastParent: PsiElement,
    place:      PsiElement
  ): Boolean = {
    val newState = `object`
      .`type`()
      .fold(
        Function.const(state),
        state.withFromType
      )

    `object`.processDeclarations(processor, newState, lastParent, place)
  }
}