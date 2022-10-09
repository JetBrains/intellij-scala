package org.jetbrains.plugins.scala
package lang
package psi
package api

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtension, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor.ProcessorUtils

trait ScPackageLike extends PsiElement {

  def findPackageObject(scope: GlobalSearchScope): Option[ScObject]

  def parentScalaPackage: Option[ScPackageLike]

  def fqn: String

  def processTopLevelDeclarations(
    processor: PsiScopeProcessor,
    state:     ResolveState,
    place:     PsiElement
  ): Boolean = {
    val processOnlyStable = ProcessorUtils.shouldProcessOnlyStable(processor)

    def processWithStableFilter(psiElement: PsiElement): Boolean = {
      val ignore = processOnlyStable && (psiElement match {
        case typed: ScTypedDefinition => !typed.isStable
        case _                        => false
      })
      if (!ignore)
        processor.execute(psiElement, state)
      else
        true
    }

    val topLevelDefs = getTopLevelDefs(place.resolveScope)
    topLevelDefs.forall {
      case ext: ScExtension             => if (!processOnlyStable) ext.extensionMethods.forall(processor.execute(_, state)) else true
      case patDef: ScPatternDefinition  => patDef.bindings.forall(processWithStableFilter)
      case varDef: ScVariableDefinition => varDef.bindings.forall(processWithStableFilter)
      case topLevelDef                  => processWithStableFilter(topLevelDef)
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