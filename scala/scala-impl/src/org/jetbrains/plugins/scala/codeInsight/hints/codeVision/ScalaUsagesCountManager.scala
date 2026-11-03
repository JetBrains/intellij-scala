//noinspection ApiStatus,UnstableApiUsage
package org.jetbrains.plugins.scala.codeInsight.hints.codeVision

import com.intellij.codeInsight.hints.codeVision.{UsageCounterConfigurationBase, UsagesCountManagerBase}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiFile, PsiReference}
import com.intellij.psi.search.PsiSearchHelper.SearchCostResult
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.{GlobalSearchScope, PsiSearchHelper, SearchScope}
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.extensions.PsiElementExt

import java.util
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters._

/**
 * Caches the number of usages of Scala declarations outside of the current file (see [[UsagesCountManagerBase]]).
 */
final class ScalaUsagesCountManager(project: Project)
  extends UsagesCountManagerBase[ScNamedElement](project, ScalaUsagesCountManager.Configuration) {

  override protected def findSupers(member: ScNamedElement): util.List[ScNamedElement] =
    util.List.of(member)

  override protected def getKey(member: ScNamedElement): String = member match {
    case td: ScTypeDefinition => td.qualifiedName
    case _ =>
      member.nameContext match {
        case m: ScMember if m.containingClass != null =>
          val signature = m match {
            case fn: ScFunction => fn.paramClauses.getText //distinguish overloads
            case _              => ""
          }
          s"${m.containingClass.qualifiedName}#${member.name}$signature"
        case _ => null
      }
  }
}

object ScalaUsagesCountManager {
  /** Returned when the search is too expensive, the hint is not shown in this case */
  final val TooManyUsages = -1

  def getInstance(project: Project): ScalaUsagesCountManager =
    project.getService(classOf[ScalaUsagesCountManager])

  private object Configuration extends UsageCounterConfigurationBase[ScNamedElement] {
    override def countUsages(file: PsiFile, members: util.List[_ <: ScNamedElement], scope: SearchScope): Int =
      members.asScala.foldLeft(0) {
        case (TooManyUsages, _) => TooManyUsages
        case (sum, member) =>
          usagesCount(member, scope) match {
            case TooManyUsages => TooManyUsages
            case count         => sum + count
          }
      }
  }

  private def usagesCount(element: ScNamedElement, scope: SearchScope): Int = {
    val searchScope = scope.intersectWith(element.getUseScope)

    cheapSearchCost(element, searchScope) match {
      case SearchCostResult.ZERO_OCCURRENCES     => 0
      case SearchCostResult.TOO_MANY_OCCURRENCES => TooManyUsages
      case _ =>
        val count = new AtomicInteger()
        ReferencesSearch.search(element, searchScope).forEach(new Processor[PsiReference] {
          override def process(reference: PsiReference): Boolean = {
            count.incrementAndGet()
            true
          }
        })
        count.get()
    }
  }

  private def cheapSearchCost(element: ScNamedElement, scope: SearchScope): SearchCostResult = {
    val name = element.name
    val isPlainIdentifier = name.nonEmpty && name.forall(Character.isJavaIdentifierPart)
    scope match {
      //the word index only knows plain identifiers, operators and backticked names are searched without the check
      case globalScope: GlobalSearchScope if isPlainIdentifier =>
        PsiSearchHelper.getInstance(element.getProject).isCheapEnoughToSearch(name, globalScope, null)
      case _ => SearchCostResult.FEW_OCCURRENCES
    }
  }
}
