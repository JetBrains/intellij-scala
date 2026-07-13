package org.jetbrains.plugins.scala.lang.psi.impl.search

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.OverridingMethodsSearch.SearchParameters
import com.intellij.psi.search.searches.{ClassInheritorsSearch, OverridingMethodsSearch}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.{Processor, QueryExecutor}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtensionBody, ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors
import org.jetbrains.plugins.scala.lang.psi.types.Context

import scala.collection.mutable

/**
 * This class is required for Ctrl+Alt+B action for cases when not PsiMethod overrides not PsiMethod (one of two cases)
 */
class MethodImplementationsSearch extends QueryExecutor[PsiElement, PsiElement] {
  override def execute(sourceElement: PsiElement, consumer: Processor[_ >: PsiElement]): Boolean = {
    sourceElement match {
      case namedElement: ScNamedElement =>
        for (implementation <- ScalaOverridingMemberSearcher.getOverridingMethods(namedElement)
             //to avoid duplicates with ScalaOverridingMemberSearcher
             if !namedElement.isInstanceOf[PsiMethod] || !implementation.is[PsiMethod]) {
          if (!consumer.process(implementation)) {
            return false
          }
        }
      case _ =>
    }
    true
  }
}

/**
 *  This class is required for Ctrl+Alt+B action for cases when PsiMethod overrides PsiMethod (no Wrappers!)
 *  That's why we need to stop processing, to avoid showing wrappers in Scala.
 *
 * Java analogue: [[com.intellij.psi.impl.search.JavaOverridingMethodsSearcher]]
 */
class ScalaOverridingMemberSearcher extends QueryExecutor[PsiMethod, OverridingMethodsSearch.SearchParameters] {
  override def execute(queryParameters: SearchParameters, consumer: Processor[_ >: PsiMethod]): Boolean = {
    val method = queryParameters.getMethod
    method match {
      case namedElement: ScNamedElement =>
        val overridingMembers = ScalaOverridingMemberSearcher.getOverridingMethods(namedElement)
        for {
          implementation <- overridingMembers
          if implementation.is[PsiMethod]
        } {
          if (!consumer.process(implementation.asInstanceOf[PsiMethod])) {
            return false
          }
        }
        false //do not process JavaOverridingMemberSearcher
      case _ => true
    }
  }
}

object ScalaOverridingMemberSearcher {
  def getOverridingMethods(method: ScNamedElement): Array[PsiNamedElement] = inReadAction {
    ScalaOverridingMemberSearcher.search(method)
  }

  def search(
    member: PsiNamedElement,
    scopeOption: Option[SearchScope] = None,
    deep: Boolean = true,
    withSelfType: Boolean = false
  ): Array[PsiNamedElement] = {
    val scope = scopeOption.getOrElse(inReadAction(member.getUseScope))

    ProgressManager.checkCanceled()

    if (!isOverridingMemberSearchApplicable(member)) {
      return Array.empty
    }

    val parentClass = member match {
      case m: PsiMethod       => m.containingClass
      case x: PsiNamedElement => PsiTreeUtil.getParentOfType(x, classOf[ScTemplateDefinition])
    }

    ProgressManager.checkCanceled()

    // e.g. if `member` is function inside Scala3 `given`
    if (parentClass == null)
      return Array.empty

    if (inReadAction(parentClass.isEffectivelyFinal))
      return Array.empty

    val inheritors: Array[PsiClass] = inReadAction {
      ClassInheritorsSearch.search(parentClass, scope, true).toArray(PsiClass.EMPTY_ARRAY)
    }

    val buffer = mutable.Set.empty[PsiNamedElement]
    // The same class may be observed more than once across different inheritor sources
    // (e.g. normal inheritors + self-type inheritors branch). Process each class once per search call.
    val processedInheritors = mutable.HashSet.empty[PsiClass]

    def process(inheritor: PsiClass): Boolean = {
      inReadAction {
        processImpl(inheritor, member, deep, withSelfType, buffer)
      }
    }

    def processOnce(inheritor: PsiClass): Boolean = {
      if (processedInheritors.add(inheritor)) process(inheritor)
      else true
    }

    var break = false
    for (inheritor <- inheritors if !break) {
      ProgressManager.checkCanceled()
      break = !processOnce(inheritor)
    }

    if (withSelfType) {
      val inheritors: Seq[ScTemplateDefinition] = ScalaInheritors.getSelfTypeInheritors(parentClass)
      break = false
      for (inheritor <- inheritors if !break) {
        ProgressManager.checkCanceled()
        break = !processOnce(inheritor)
      }
    }

    buffer.toArray
  }

  private def isOverridingMemberSearchApplicable(member: PsiNamedElement): Boolean = {
    def inTemplateBodyOrEarlyDef(element: PsiElement): Boolean = {
      val parent = inReadAction(element.getParent)
      parent match {
        case _: ScTemplateBody | _: ScEarlyDefinitions => true
        case _: ScExtensionBody => true
        case _ => false
      }
    }

    member match {
      case _: ScFunction | _: ScTypeAlias =>
        inTemplateBodyOrEarlyDef(member)
      case td: ScTypeDefinition if !td.isObject =>
        inTemplateBodyOrEarlyDef(member)
      case cp: ScClassParameter if cp.isClassMember =>
        true
      case x: PsiNamedElement =>
        val nameContext = x.nameContext
        nameContext != null && inTemplateBodyOrEarlyDef(nameContext)
      case _ =>
        false
    }
  }

  /**
   * Collects overriding members of `originalMember` for a single `inheritor`.
   *
   * For term members, only members with a source-level implementation origin are collected:
   *  - members defined directly in `inheritor`,
   *  - exported members physically declared in `inheritor`,
   *  - members defined in templates that are applicable through inheritor self-type constraints.
   *
   * Members copied into an inheritor by Scala 2 mixin composition, including self-type mixins, are excluded
   * unless the inheritor has its own source-level declaration. The Scala 2 compiler physically copies
   * implementations from mixed-in definitions, so those copied members must not be mistaken for declarations
   * owned by the inheritor.
   *
   * For example, when searching for implementations of `Base.run`, the marked members are handled as follows:
   * {{{
   * trait Base { def run(): Unit }
   *
   * class Direct extends Base {
   *   override def run(): Unit = () // included: declared directly in Direct
   * }
   *
   * trait SelfTyped { this: Base =>
   *   override def run(): Unit = () // included: SelfTyped's self-type is satisfied by DirectSelfType
   * }
   * class DirectSelfType extends Base with SelfTyped
   *
   * trait Mixin { def run(): Unit = () }
   * class Exported extends Base {
   *   val delegate: Mixin = new Mixin {}
   *   export delegate.run // included: export declared in Exported
   * }
   * class MixedInOnly extends Base with Mixin // excluded: inherited mixed-in member
   * }}}
   *
   * @return `false` when search should stop early (`deep = false` and a match was found), otherwise `true`.
   */
  private def processImpl(
    inheritor: PsiClass,
    originalMember: PsiNamedElement,
    deep: Boolean,
    withSelfType: Boolean,
    resultBuffer: mutable.Set[PsiNamedElement]
  ): Boolean = {
    implicit val context: Context = Context(originalMember)

    def collectInheritorsOfType(name: String): Boolean = {
      inheritor match {
        case inheritor: ScTypeDefinition =>
          for (alias <- inheritor.aliases if name == alias.name) {
            resultBuffer += alias
            if (!deep)
              return false
          }
          for (td <- inheritor.typeDefinitions if !td.isObject && name == td.name) {
            resultBuffer += td
            if (!deep)
              return false
          }
        case _ =>
      }
      true
    }

    originalMember match {
      case alias: ScTypeAlias =>
        val continue = collectInheritorsOfType(alias.name)
        if (!continue)
          return false
      case td: ScTypeDefinition if !td.isObject =>
        val continue = collectInheritorsOfType(td.name)
        if (!continue)
          return false
      case _: PsiNamedElement =>
        val signatures: TypeDefinitionMembers.TermNodes.Map =
          if (withSelfType) TypeDefinitionMembers.getSelfTypeSignatures(inheritor)
          else TypeDefinitionMembers.getSignatures(inheritor)
        val originalMemberName = Option(originalMember.name) match {
          case Some(value) => value
          case _ =>
            return true
        }
        val signsIterator = signatures.forName(originalMemberName).nodesIterator
        while (signsIterator.hasNext) {
          val node = signsIterator.next()

          // Keep only source-level implementation origins: Declared, Exported, and SelfTypeSuper.
          // Reject NominalSuper to avoid mixed-in fallback targets.
          // This shifts filtering from reverse self-type index lookups on each inheritor to the already-computed
          // signature origin of each node.
          //
          // NOTE: For a java version of the searcher a similar filtering is done in `ScTypeDefinitionImpl.psiMethods`
          // It's not a direct alternative, but still it's related.
          if (node.isAllowedImplementationSource) {
            val supersIterator = node.supers.iterator
            while (supersIterator.hasNext) {
              val s = supersIterator.next()
              if (s.info.namedElement eq originalMember) {
                resultBuffer += node.info.namedElement
                return deep
              }
            }
          }
        }
    }
    true
  }
}
