package org.jetbrains.plugins.scala
package findUsages.factory

import java.util

import com.intellij.find.findUsages.{AbstractFindUsagesDialog, FindUsagesHandler, FindUsagesOptions}
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.ImplicitReferencesSearch
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScForBinding, ScGenerator}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaOverridingMemberSearcher
import org.jetbrains.plugins.scala.lang.psi.light._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.util.ImplicitUtil.ImplicitSearchTarget

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.08.2009
 */

class ScalaFindUsagesHandler(element: PsiElement, factory: ScalaFindUsagesHandlerFactory)
        extends FindUsagesHandler(element) {

  override def getPrimaryElements: Array[PsiElement] = {
    def applyFactoryMethods(c: ScClass) = {
      val companion = getCompanionModule(c)
      val applyMethods = companion.toSeq.flatMap(_.functionsByName("apply"))
      applyMethods.filter {
        case f: ScFunctionDefinition if f.isApplyMethod => f.isSynthetic
        case f: ScFunctionDefinition =>
          val returnType = f.returnType.toOption
          returnType.exists(_.equiv(ScDesignatorType(c)))
        case _ => false
      }
    }

    element match {
      case c: ScClass if factory.typeDefinitionOptions.isOnlyNewInstances =>
        val constructors = c.constructors
        (constructors ++ applyFactoryMethods(c)).toArray
      case _ => Array(element)
    }
  }

  override def getStringsToSearch(element: PsiElement): util.Collection[String] = {
    val result: util.Set[String] = new util.HashSet[String]()
    element match {
      case t: ScTrait =>
        result.add(t.name)
        result.add(t.getName)
        result.add(t.fakeCompanionClass.getName)
        t.fakeCompanionModule match {
          case Some(o) => result.add(o.getName)
          case _ =>
        }
      case o: ScObject =>
        result.add(o.name)
        result.add(o.getName)
      case c: ScClass =>
        result.add(c.name)
        c.fakeCompanionModule match {
          case Some(o) => result.add(o.getName)
          case _ =>
        }
      case named: PsiNamedElement =>
        val name = named.name
        result.add(name)
        nameContext(named) match {
          case v: ScValue if isBeanProperty(v) =>
            result.add("get" + StringUtil.capitalize(name))
          case v: ScVariable if isBeanProperty(v) =>
            result.add("get" + StringUtil.capitalize(name))
            result.add("set" + StringUtil.capitalize(name))
          case v: ScValue if isBooleanBeanProperty(v) =>
            result.add("is" + StringUtil.capitalize(name))
          case v: ScVariable if isBooleanBeanProperty(v) =>
            result.add("is" + StringUtil.capitalize(name))
            result.add("set" + StringUtil.capitalize(name))
          case _ =>
        }
      case _ => result.add(element.getText)
    }
    result
  }

  override def getFindUsagesOptions(dataContext: DataContext): FindUsagesOptions = {
    element match {
      case _: ScTypeDefinition => factory.typeDefinitionOptions
      case inNameContext(m: ScMember) if !m.isLocal => factory.memberOptions
      case _: ScParameter | _: ScTypeParam |
           inNameContext(_: ScMember | _: ScCaseClause | _: ScGenerator | _: ScForBinding ) => factory.localOptions
      case _ => super.getFindUsagesOptions(dataContext)
    }
  }

  override def getSecondaryElements: Array[PsiElement] = {
    element match {
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
          a.map(role => t.getTypedDefinitionWrapper(isStatic = false, isInterface = false, role = role, cClass = None))
        }
      case _ => Array.empty
    }
  }

  override def getFindUsagesDialog(isSingleFile: Boolean, toShowInNewTab: Boolean, mustOpenInNewTab: Boolean): AbstractFindUsagesDialog = {
    element match {
      case t: ScTypeDefinition => new ScalaTypeDefinitionUsagesDialog(t, getProject, getFindUsagesOptions,
        toShowInNewTab, mustOpenInNewTab, isSingleFile, this)
      case _ => super.getFindUsagesDialog(isSingleFile, toShowInNewTab, mustOpenInNewTab)
    }
  }

  override def processUsagesInText(element: PsiElement, processor: Processor[UsageInfo], searchScope: GlobalSearchScope): Boolean = {
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
    processor: Processor[UsageInfo],
    options:   FindUsagesOptions
  ): Boolean = {
    def addElementUsages(e: PsiElement): Boolean =
      super.processElementUsages(e, processor, options)

    if (!addElementUsages(element)) return false
    inReadAction {
      options match {
        case s: ScalaTypeDefinitionFindUsagesOptions =>
          val definition = element.asInstanceOf[ScTypeDefinition]

          if (s.isMembersUsages) {
            definition.members.foreach {
              case v: ScValueOrVariable => v.declaredElements.foreach(d => if (!addElementUsages(d)) return false)
              case member: ScMember     => if (!addElementUsages(member)) return false
            }

            definition match {
              case c: ScClass =>
                for {
                  constructor <- c.constructor.toSeq
                  clause      <- constructor.effectiveParameterClauses if !clause.isImplicit
                  param       <- clause.effectiveParameters
                } if (!addElementUsages(param)) return false
            }
          }

          if (s.isSearchCompanionModule)
            definition.baseCompanionModule.foreach(companion => if (!addElementUsages(companion)) return false)

          if (s.isImplementingTypeDefinitions) {
            val success = ClassInheritorsSearch.search(definition, true).forEach((cls: PsiClass) => cls match {
              case _: PsiClassWrapper => true
              case aClass: PsiClass   => processor.process(new UsageInfo(aClass))
              case _                  => true
            })
            if (!success) return false
          }
        case _ =>
      }

      element match {
        case (named: ScNamedElement) && inNameContext(member: ScMember) if !member.isLocal =>
          ScalaOverridingMemberSearcher.search(named).foreach(e => if (!addElementUsages(e)) return false)
        case _ =>
      }

      element match {
        case ImplicitSearchTarget(target) =>
          val success =
            ImplicitReferencesSearch.search(target).forEach((e: PsiReference) => processor.process(new UsageInfo(e)))
          if (!success) return false
        case _ => ()
      }
    }
    true
  }

  override def isSearchForTextOccurrencesAvailable(psiElement: PsiElement, isSingleFile: Boolean): Boolean = !isSingleFile
}
