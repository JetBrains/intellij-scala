package org.jetbrains.plugins.scala
package findUsages.factory

import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.search.CompilerIndicesInheritorsSearch
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaOverridingMemberSearcher
import org.jetbrains.plugins.scala.lang.psi.light._
import org.jetbrains.plugins.scala.util.SAMUtil._
import org.jetbrains.plugins.scala.util.ScalaUsageNamesUtil

import java.util

class ScalaFindUsagesHandler(element: PsiElement, factory: ScalaFindUsagesHandlerFactory)
        extends ScalaFindUsagesHandlerBase(element, factory) {

  override def getStringsToSearch(element: PsiElement): util.Collection[String] = ScalaUsageNamesUtil.getStringsToSearch(element)

  override def getSecondaryElements: Array[PsiElement] = {
    element match {
      case e: ScEnumCase => Array(e.getSyntheticCounterpart)
      case t: ScObject =>
        t.fakeCompanionClass match {
          case Some(clazz) => Array(clazz)
          case _ => Array.empty
        }
      case t: ScTrait => Array(t.fakeCompanionClass)
      case f: ScFunction if Seq("apply", "unapply", "unapplySeq").contains(f.name) =>
        f.containingClass match {
          case obj: ScObject if obj.isSyntheticObject => Array(obj)
          case _ => Array.empty
        }
      case t: ScTypedDefinition =>
        getBeanMethods(t).toArray ++ {
          val a = t.nameContext match {
            case v: ScValue if isBeanProperty(v) => Array(GETTER)
            case v: ScVariable if isBeanProperty(v) => Array(GETTER, SETTER)
            case v: ScValue if isBooleanBeanProperty(v) => Array(IS_GETTER)
            case v: ScVariable if isBooleanBeanProperty(v) => Array(IS_GETTER, SETTER)
            case _ => Array.empty[DefinitionRole]
          }
          a.map[PsiElement](role => t.getTypedDefinitionWrapper(isStatic = false, isAbstract = false, role = role, cClass = None))
        }
      case _ => Array.empty
    }
  }

  override def processUsagesInText(element: PsiElement, processor: Processor[_ >: UsageInfo], searchScope: GlobalSearchScope): Boolean = {
    val nonScalaTextProcessor = new Processor[UsageInfo] {
      override def process(t: UsageInfo): Boolean = {
        if (t.getFile.getFileType == ScalaFileType.INSTANCE) true
        else processor.process(t)
      }
    }
    super.processUsagesInText(element, nonScalaTextProcessor, searchScope)
  }

  override def processElementUsages(
    element:   PsiElement,
    processor: Processor[_ >: UsageInfo],
    options:   FindUsagesOptions
  ): Boolean = {

    if (!super.processElementUsages(element, processor, options)) return false

    val scalaOptions = options.asOptionOf[ScalaTypeDefinitionFindUsagesOptions].getOrElse(return true)

    processMemberUsages(element, processor, scalaOptions) &&
      processCompanionUsages(element, processor, scalaOptions) &&
      processOverridingMembers(element, processor, scalaOptions)
      processSamUsagesWithCompilerReferences(element, processor, scalaOptions) &&
      processImplementingTypeDefinitionsUsages(element, processor, scalaOptions)
  }

  override def isSearchForTextOccurrencesAvailable(psiElement: PsiElement, isSingleFile: Boolean): Boolean = !isSingleFile

  private def processSamUsagesWithCompilerReferences(element: PsiElement,
                                                     processor: Processor[_ >: UsageInfo],
                                                     options: ScalaTypeDefinitionFindUsagesOptions): Boolean = {
    element match {
      case definition: ScTypeDefinition if factory.compilerIndicesOptions.isEnabledForSAMTypes && inReadAction(definition.isSAMable) =>
        CompilerIndicesInheritorsSearch.search(definition, options.searchScope)
          .forEach((e: PsiElement) => processor.process(new UsageInfo(e)))
      case _ => true
    }
  }

  private def processImplementingTypeDefinitionsUsages(element: PsiElement,
                                                       processor: Processor[_ >: UsageInfo],
                                                       options: ScalaTypeDefinitionFindUsagesOptions): Boolean = {
    element match {
      case definition: ScTypeDefinition if options.isImplementingTypeDefinitions =>
        ClassInheritorsSearch.search(definition, true).forEach((cls: PsiClass) => cls match {
          case _: PsiClassWrapper => true
          case aClass: PsiClass   => processor.process(new UsageInfo(aClass))
          case _                  => true
        })
      case _ => true
    }
  }

  private def processMemberUsages(element: PsiElement,
                                  processor: Processor[_ >: UsageInfo],
                                  options: ScalaTypeDefinitionFindUsagesOptions): Boolean = {
    element match {
      case definition: ScTypeDefinition if options.isMembersUsages =>
        val members = inReadAction {
          val bodyMembers = definition.members.flatMap {
            case v: ScValueOrVariable => v.declaredElements
            case member: ScMember     => Seq(member)
          }
          val classParams = definition match {
            case c: ScClass =>
              for {
                constructor <- c.constructor.toSeq
                clause      <- constructor.effectiveParameterClauses if !clause.isImplicit
                param       <- clause.effectiveParameters
              } yield param
            case _ =>
              Seq.empty
          }
          bodyMembers ++ classParams
        }
        members.forall(super.processElementUsages(_, processor, options))
      case _ => true
    }
  }

  private def processCompanionUsages(element: PsiElement,
                                     processor: Processor[_ >: UsageInfo],
                                     options: ScalaTypeDefinitionFindUsagesOptions): Boolean =
    element match {
      case definition: ScTypeDefinition if options.isSearchCompanionModule =>
        val companion = inReadAction(definition.baseCompanion)
        companion.forall(super.processElementUsages(_, processor, options))
      case _ => true
    }

  private def processOverridingMembers(element: PsiElement,
                                       processor: Processor[_ >: UsageInfo],
                                       options: ScalaTypeDefinitionFindUsagesOptions): Boolean = {
    val overriding = inReadAction {
      element match {
        case (named: ScNamedElement) && inNameContext(member: ScMember) if member.isDefinedInClass =>
          ScalaOverridingMemberSearcher.search(named)
        case _ => Array.empty[PsiNamedElement]
      }
    }
    overriding.forall(super.processElementUsages(_, processor, options))
  }
}
