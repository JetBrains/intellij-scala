package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.find.findUsages.{AbstractFindUsagesDialog, FindUsagesOptions, FindUsagesHandler}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import com.intellij.psi.{PsiClass, PsiNamedElement, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.extensions.toPsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScClass, ScTrait, ScObject}
import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper.DefinitionRole._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScFunction, ScVariable, ScValue}
import org.jetbrains.plugins.scala.lang.psi.light._
import com.intellij.openapi.actionSystem.DataContext
import java.util
import com.intellij.util.Processor
import com.intellij.usageView.UsageInfo
import com.intellij.psi.search.searches.ClassInheritorsSearch
import collection.mutable
import org.jetbrains.plugins.scala.util.ScalaUtil
import com.intellij.openapi.ui.Messages
import org.jetbrains.plugins.scala.ScalaBundle
import scala.Array
import com.intellij.CommonBundle
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaOverridengMemberSearch

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.08.2009
 */

class ScalaFindUsagesHandler(element: PsiElement) extends {
    private val _replacedElement: PsiElement = {
      element match {
        case wrapper: PsiClassWrapper => wrapper.definition
        case p: PsiTypedDefinitionWrapper => p.typedDefinition
        case p: StaticPsiTypedDefinitionWrapper => p.typedDefinition
        case f: ScFunctionWrapper => f.function
        case f: FakePsiMethod => f.navElement
        case s: StaticPsiMethodWrapper => s.method
        case _ => element
      }

    }
  } with FindUsagesHandler(_replacedElement) {
  private var replacedElement = _replacedElement

  override def getPrimaryElements: Array[PsiElement] = {
    _replacedElement match {
      case function: ScFunction =>
        val signs = function.superSignatures
        if (signs.length == 0 || signs.last.namedElement.isEmpty) Array(function)
        else {
          val result = Messages.showDialog(element.getProject, ScalaBundle.message("find.usages.method.has.supers", function.name), "Warning",
            Array(CommonBundle.getYesButtonText, CommonBundle.getNoButtonText, CommonBundle.getCancelButtonText), 0, Messages.getQuestionIcon
          )
          result match {
            case 0 =>
              val elem = signs.last.namedElement.get
              replacedElement = elem
              Array(elem)
            case 1 => Array(function)
            case 2 => Array.empty
          }
        }
      case _ => Array(_replacedElement)
    }
  }

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
    replacedElement match {
      case t: ScTypeDefinition => new ScalaTypeDefinitionFindUsagesOptions(t, getProject, dataContext)
      case _ => super.getFindUsagesOptions(dataContext)
    }
  }

  override def getSecondaryElements: Array[PsiElement] = {
    replacedElement match {
      case t: ScObject =>
        t.fakeCompanionClass match {
          case Some(clazz) => Array(clazz)
          case _ => Array.empty
        }
      case t: ScTrait => Array(t.fakeCompanionClass)
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
    replacedElement match {
      case t: ScTypeDefinition => new ScalaTypeDefinitionUsagesDialog(t, getProject, getFindUsagesOptions,
        toShowInNewTab, mustOpenInNewTab, isSingleFile, this)
      case _ => super.getFindUsagesDialog(isSingleFile, toShowInNewTab, mustOpenInNewTab)
    }
  }

  override def processElementUsages(element: PsiElement, processor: Processor[UsageInfo], options: FindUsagesOptions): Boolean = {
    if (!super.processElementUsages(element, processor, options)) return false
    options match {
      case s: ScalaTypeDefinitionFindUsagesOptions =>
        val clazz = replacedElement.asInstanceOf[ScTypeDefinition]
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
            ScalaUtil.readAction(getProject) {
              if (!processor.process(new UsageInfo(c))) return false
            }
          }
        }
      case _ =>
    }

    element match {
      case function: ScFunction =>
        for (elem <- ScalaOverridengMemberSearch.search(function, deep = true)) {
          if (!super.processElementUsages(elem, processor, options)) return false
        }
      case _ =>
    }
    true
  }
}