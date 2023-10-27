package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.ScExportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScPackageLike.{canProcessExport, withReentrancyGuard}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtension, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScExportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor.ProcessorUtils
import org.jetbrains.plugins.scala.util.UnloadableThreadLocal

import java.{util => ju}

trait ScPackageLike extends PsiElement {

  def findPackageObject(scope: GlobalSearchScope): Option[ScObject]

  def parentScalaPackage: Option[ScPackageLike]

  def fqn: String

  //NOTE: only process top level definitions which are not type definition
  //e.g. it will process `def`, `val` but not `class` or `object`
  def processTopLevelDeclarations(
    processor:  PsiScopeProcessor,
    state:      ResolveState,
    lastParent: PsiElement,
    place:      PsiElement
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

    val shouldStop = !processTopLevelExports(processor, state, place.resolveScope, lastParent, place)

    if (shouldStop) false
    else {
      val topLevelDefs = getTopLevelDefs(place)

      topLevelDefs.forall {
        case ext: ScExtension             => if (!processOnlyStable) ext.extensionMethods.forall(processor.execute(_, state)) else true
        case patDef: ScPatternDefinition  => patDef.bindings.forall(processWithStableFilter)
        case varDef: ScVariableDefinition => varDef.bindings.forall(processWithStableFilter)
        case topLevelDef                  => processWithStableFilter(topLevelDef)
      }
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

  private def processTopLevelExports(
    proc:       PsiScopeProcessor,
    state:      ResolveState,
    scope:      GlobalSearchScope,
    lastParent: PsiElement,
    place:      PsiElement
  ): Boolean = {
    val psiManager             = ScalaPsiManager.instance(getProject)
    val topLevelExports        = psiManager.getTopLevelExportsByPackage(fqn, scope)
    val topLevelExportsHolders = topLevelExports.groupBy(_.getContext).keys.iterator
    var shouldStop             = false

    while (topLevelExportsHolders.hasNext && !shouldStop) {
      val exportHolder = topLevelExportsHolders.next()

      exportHolder match {
        case eh: ScExportsHolder =>
          val exports = eh.getExportStatements.iterator
          while (exports.hasNext) {
            val exportStmt = exports.next()
            val canProcess = canProcessExport(exportStmt)

            if (!canProcess) shouldStop = true
            else
              shouldStop =
                withReentrancyGuard(exportStmt)(!exportStmt.processDeclarations(proc, state, lastParent, place))
          }
        case other => throw new IllegalStateException(s"Unexpected exports holder $other")
      }
    }

    !shouldStop
  }
}

object ScPackageLike {
  private val processingExport: UnloadableThreadLocal[ju.Set[ScExportStmt]] =
    new UnloadableThreadLocal[ju.Set[ScExportStmt]](new ju.HashSet)

  /**
   * Exports are processed in batches, grouped by their ScExportsHolder,
   * it is important to process them in the same order as they are declared in the file.
   * We start processing export only if:
   * 1. We are not currently processing export from another ScExportsHolder
   * 2. We are not currently processing the same export (because we always process them in the order of
   *    declaration, this also means that, we never process export number n + 1, until we finish processing export number n).
   */
  private def canProcessExport(exportStmt: ScExportStmt): Boolean = {
    var canProcess = true

    processingExport.value.forEach { exp =>
      val isReentrant      = exp == exportStmt
      val inAnotherContext = exportStmt.getContext != exp.getContext

      if (isReentrant || inAnotherContext) canProcess = false
    }

    canProcess
  }

  private def withReentrancyGuard[T](exportStmt: ScExportStmt)(action: =>T): T =
    try {
      processingExport.value.add(exportStmt)
      action
    } finally {
      processingExport.value.remove(exportStmt)
    }

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