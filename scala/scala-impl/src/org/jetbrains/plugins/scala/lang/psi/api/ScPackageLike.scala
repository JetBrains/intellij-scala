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
        case ext: ScExtension =>
          if (!processOnlyStable) {
            //note: the extension methods itself were are processed as functions because
            //`topLevelDefs` returns them as well, so there is no need to iterate over them
            ext.extensionBody.forall { body =>
              body.processDeclarationsFromExports(
                processor,
                state.withExportedIn(body),
                lastParent,
                place
              )
            }
          }
          else true
        case patDef: ScPatternDefinition  => patDef.bindings.forall(processWithStableFilter)
        case varDef: ScVariableDefinition => varDef.bindings.forall(processWithStableFilter)
        case topLevelDef                  => processWithStableFilter(topLevelDef)
      }
    }
  }

  private def getTopLevelDefs(place: PsiElement): Iterable[ScMember] = {
    val scope = place.resolveScope
    ScalaPsiManager.instance(getProject).getTopLevelDefinitionsByPackage(fqn, scope)
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