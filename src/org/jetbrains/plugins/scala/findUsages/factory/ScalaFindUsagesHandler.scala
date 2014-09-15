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
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaOverridingMemberSearcher
import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper.DefinitionRole._
import org.jetbrains.plugins.scala.lang.psi.light._

import _root_.scala.collection.mutable

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.08.2009
 */

class ScalaFindUsagesHandler(element: PsiElement, factory: ScalaFindUsagesHandlerFactory)
        extends FindUsagesHandler(element) {

  override def getPrimaryElements: Array[PsiElement] = Array(element)
  override def getStringsToSearch(element: PsiElement): util.Collection[String] = {
    val result: util.Set[String] = new util.HashSet[String]()
    element match {
      case t: ScTrait =>
        result.add(t.name)
        result.add(t.getName)
        result.add(t.fakeCompanionClass.getName)
      case o: ScObject =>
        result.add(o.name)
        result.add(o.getName)
      case c: ScClass if c.isCase =>
        result.add(c.name)
        c.fakeCompanionModule match {
          case Some(o) => result.add(o.getName)
          case _ =>
        }
      case named: PsiNamedElement =>
        val name = named.name
        result.add(name)
        ScalaPsiUtil.nameContext(named) match {
          case v: ScValue if ScalaPsiUtil.isBeanProperty(v) =>
            result.add("get" + StringUtil.capitalize(name))
          case v: ScVariable if ScalaPsiUtil.isBeanProperty(v) =>
            result.add("get" + StringUtil.capitalize(name))
            result.add("set" + StringUtil.capitalize(name))
          case v: ScValue if ScalaPsiUtil.isBooleanBeanProperty(v) =>
            result.add("is" + StringUtil.capitalize(name))
          case v: ScVariable if ScalaPsiUtil.isBooleanBeanProperty(v) =>
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
      case t: ScTypeDefinition => factory.typeDefinitionOptions
      case m: ScMember => factory.memberOptions
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
        t.getBeanMethods.toArray ++ {
          val a: Array[DefinitionRole] = t.nameContext match {
            case v: ScValue if ScalaPsiUtil.isBeanProperty(v) => Array(GETTER)
            case v: ScVariable if ScalaPsiUtil.isBeanProperty(v) => Array(GETTER, SETTER)
            case v: ScValue if ScalaPsiUtil.isBooleanBeanProperty(v) => Array(IS_GETTER)
            case v: ScVariable if ScalaPsiUtil.isBooleanBeanProperty(v) => Array(IS_GETTER, SETTER)
            case _ => Array.empty
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
        if (t.getFile.getFileType == ScalaFileType.SCALA_FILE_TYPE) true
        else processor.process(t)
      }
    }
    super.processUsagesInText(element, nonScalaTextProcessor, searchScope)
  }

  override def processElementUsages(element: PsiElement, processor: Processor[UsageInfo], options: FindUsagesOptions): Boolean = {
    if (!super.processElementUsages(element, processor, options)) return false
    options match {
      case s: ScalaTypeDefinitionFindUsagesOptions if element.isInstanceOf[ScTypeDefinition] =>
        val clazz = element.asInstanceOf[ScTypeDefinition]
        if (s.isMembersUsages) {
          clazz.members.foreach {
            case fun: ScFunction =>
              if (!super.processElementUsages(fun, processor, options)) return false
            case v: ScValue =>
              v.declaredElements.foreach { d =>
                if (!super.processElementUsages(d, processor, options)) return false
              }
            case v: ScVariable =>
              v.declaredElements.foreach { d =>
                if (!super.processElementUsages(d, processor, options)) return false
              }
            case ta: ScTypeAlias =>
              if (!super.processElementUsages(ta, processor, options)) return false
            case c: ScTypeDefinition =>
              if (!super.processElementUsages(c, processor, options)) return false
            case c: ScPrimaryConstructor =>
              if (!super.processElementUsages(c, processor, options)) return false
          }
          clazz match {
            case c: ScClass =>
              c.constructor match {
                case Some(constr) => constr.effectiveParameterClauses.foreach {clause =>
                  clause.parameters.foreach {param =>
                    if (!super.processElementUsages(c, processor, options)) return false
                  }
                }
                case _ =>
              }
            case _ =>
          }
        }
        if (s.isSearchCompanionModule) {
          ScalaPsiUtil.getBaseCompanionModule(clazz) match {
            case Some(companion) =>
              if (!super.processElementUsages(companion, processor, options)) return false
            case _ =>
          }
        }
        if (s.isImplementingTypeDefinitions) {
          val res = new mutable.HashSet[PsiClass]()
          ClassInheritorsSearch.search(clazz, true).forEach(new Processor[PsiClass] {
            def process(t: PsiClass): Boolean = {
              t match {
                case p: PsiClassWrapper =>
                case _ => res += t
              }
              true
            }
          })
          res.foreach { c =>
            val processed = inReadAction(processor.process(new UsageInfo(c)))
            if (!processed) return false
          }
        }
      case _ =>
    }

    element match {
      case function: ScFunction if !function.isLocal =>
        for (elem <- ScalaOverridingMemberSearcher.search(function, deep = true)) {
          val processed = inReadAction(super.processElementUsages(elem, processor, options))
          if (!processed) return false
        }
      case _ =>
    }
    true
  }

  override def isSearchForTextOccurencesAvailable(psiElement: PsiElement, isSingleFile: Boolean): Boolean = !isSingleFile
}