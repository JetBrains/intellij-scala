package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtension, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor.ProcessorUtils

trait ScPackageLike extends PsiElement {

  def findPackageObject(scope: GlobalSearchScope): Option[ScObject]

  def parentScalaPackage: Option[ScPackageLike]

  def fqn: String

  //NOTE: only process top level definitions which are not type definition
  //e.g. it will process `def`, `val` but not `class` or `object`
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

    val topLevelDefs = getTopLevelDefs(place)
    topLevelDefs.forall {
      case ext: ScExtension             => if (!processOnlyStable) ext.extensionMethods.forall(processor.execute(_, state)) else true
      case patDef: ScPatternDefinition  => patDef.bindings.forall(processWithStableFilter)
      case varDef: ScVariableDefinition => varDef.bindings.forall(processWithStableFilter)
      case topLevelDef                  => processWithStableFilter(topLevelDef)
    }
  }

  private def getTopLevelDefs(place: PsiElement): Iterable[ScMember] = {
    val placeFile = place.getContainingFile

    val resolveScope: GlobalSearchScope = {
      //NOTE:
      //We shouldn't get top level definitions for the file of `place` from index.
      //This is required mainly for SCL-15926, see tests:
      //  ScalaBasicCompletionTest.testLocalValueName1_TopLevel
      //  ScalaBasicCompletionTest.testLocalValueName2_TopLevel
      //  ScalaBasicCompletionTest.testLocalValueName3_TopLevel
      //
      //During completion a special synthetic file is created which is equal to the original file except that it
      //has a special injected reference (see com.intellij.codeInsight.completion.CompletionUtilCore.DUMMY_IDENTIFIER)
      //We need top level members to be resolved to members from the synthetic file, not from the original file.
      //It may be important e.g. when checking if the resolve result is an ancessor of the original place
      //See org.jetbrains.plugins.scala.lang.completion.ScalaBasicCompletionProvider.DefaultCompletionProcessor.isValidLocalDefinition
      val placeOriginalFile = placeFile.getOriginalFile
      val notOriginalPlaceFile = GlobalSearchScope.notScope(GlobalSearchScope.fileScope(place.getProject, placeOriginalFile.getVirtualFile))
      place.resolveScope.intersectWith(notOriginalPlaceFile)
    }

    val topLevelDefs = ScalaPsiManager.instance(getProject).getTopLevelDefinitionsByPackage(fqn, resolveScope)
    //we need to place file top level members because we excluded them from the resolve scope
    val topLevelDefsInPlaceFile = placeFile match {
      case sf: ScalaFile =>
        val members = sf.members
        members.filterNot(_.isInstanceOf[ScTypeDefinition])
          .filter(_.topLevelQualifier.contains(fqn))
      case _ => Iterable.empty
    }

    val result = topLevelDefsInPlaceFile ++ topLevelDefs
    result
  }
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