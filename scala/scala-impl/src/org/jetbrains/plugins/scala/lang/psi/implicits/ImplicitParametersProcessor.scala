package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiNamedElement, ResolveState}
import com.intellij.util.containers.SmartHashSet
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.isImplicit
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt

import java.{util => ju}
import scala.collection.mutable
import scala.jdk.CollectionConverters.SetHasAsScala

private[implicits] final class ImplicitParametersProcessor(
  override protected val getPlace: PsiElement,
  override protected val withoutPrecedence: Boolean
) extends ImplicitProcessor(getPlace, withoutPrecedence) {

  private[this] val levelSets: mutable.ArrayBuffer[ju.Set[ScalaResolveResult]] = {
    val buffer = new mutable.ArrayBuffer[ju.Set[ScalaResolveResult]]()
    buffer += new SmartHashSet[ScalaResolveResult]()
    buffer
  }

  override protected def execute(
    namedElement: PsiNamedElement
  )(implicit
    state: ResolveState
  ): Boolean = {
    val isDeclaredOrExportedInExtension =
      ImplicitProcessor.isDeclaredOrExportedInExtension(namedElement, state)

    if ((isImplicit(namedElement) || isDeclaredOrExportedInExtension)
      && isAccessible(namedElement, isDeclaredOrExportedInExtension)) {
      addResult(
        new ScalaResolveResult(
          namedElement,
          state.substitutorWithThisType,
          renamed           = state.renamed,
          fromType          = state.fromType,
          importsUsed       = state.importsUsed,
          implicitScopeType = state.implicitScopeType,
          isExtensionCall   = isDeclaredOrExportedInExtension,
          exportedInfo      = state.exportedInfo
        )
      )
    }

    true
  }

  private def currentLevelSet: ju.Set[ScalaResolveResult] =
    levelSets(levelSets.size - 1)

  override def getLevelSet: ju.Set[ScalaResolveResult] = currentLevelSet

  override def changedLevel: Boolean = {
    val levelSet = currentLevelSet

    if (!levelSet.isEmpty) {
      levelSets += new SmartHashSet[ScalaResolveResult]()

      uniqueNamesSet.addAll(levelUniqueNamesSet)
      levelUniqueNamesSet.clear()
    }

    true
  }

  /**
   * Introduced to account for scala 3 taking nesting into account, when resolving implicits.
   * https://dotty.epfl.ch/3.0.0/docs/reference/changed-features/implicit-resolution.html
   */
  def candidatesByLevel: collection.Seq[collection.Set[ScalaResolveResult]] = {
    treeWalkUp()
    levelSets.map(_.asScala)
  }

  override def candidatesS: Set[ScalaResolveResult] = {
    val flattened = Set.newBuilder[ScalaResolveResult]
    flattened   ++= candidatesSet
    val iter      = candidatesByLevel.iterator

    while (iter.hasNext) {
      flattened ++= iter.next()
    }

    flattened.result()
  }

  private def isContextAncestor(e: PsiNamedElement): Boolean = {
    val nameContext = e.nameContext
    nameContext match {
      case _: ScCaseClause => !getPlace.betterMonadicForEnabled && !getPlace.isInScala3File
      case _               => PsiTreeUtil.isContextAncestor(nameContext, getPlace, false)
    }
  }

  private def isAccessible(namedElement: PsiNamedElement, isExtensionCall: Boolean): Boolean = {
    val notContextAncestor = isExtensionCall || !isContextAncestor(namedElement)
    val accesible          = isPredefPriority || ImplicitProcessor.isAccessible(namedElement, getPlace)
    notContextAncestor && accesible
  }
}
