package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.find.findUsages.FindUsagesHandler
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import com.intellij.psi.{PsiNamedElement, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import com.intellij.openapi.util.text.StringUtil
import java.util.{Collection, HashSet, Set}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.extensions.toPsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTrait, ScObject}
import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper.DefinitionRole._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScAnnotationsHolder, ScVariable, ScValue}
import org.jetbrains.plugins.scala.lang.psi.light._

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.08.2009
 */

class ScalaFindUsagesHandler(element: PsiElement) extends {
    val replacedElement: PsiElement = {
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
  } with FindUsagesHandler(replacedElement) {
  override def getStringsToSearch(element: PsiElement): Collection[String] = {
    val result: Set[String] = new HashSet[String]()
    replacedElement match {
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
          case v: ScValue if v.hasAnnotation("scala.reflect.BeanProperty").isDefined =>
            result.add("get" + StringUtil.capitalize(name))
          case v: ScVariable if v.hasAnnotation("scala.reflect.BeanProperty").isDefined =>
            result.add("get" + StringUtil.capitalize(name))
            result.add("set" + StringUtil.capitalize(name))
          case v: ScValue if v.hasAnnotation("scala.reflect.BooleanBeanProperty").isDefined =>
            result.add("is" + StringUtil.capitalize(name))
          case v: ScVariable if v.hasAnnotation("scala.reflect.BooleanBeanProperty").isDefined =>
            result.add("is" + StringUtil.capitalize(name))
            result.add("set" + StringUtil.capitalize(name))
          case _ =>
        }
      case _ => result.add(element.getText)
    }
    result
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
            case v: ScValue if v.hasAnnotation("scala.reflect.BeanProperty").isDefined => Array(GETTER)
            case v: ScVariable if v.hasAnnotation("scala.reflect.BeanProperty").isDefined => Array(GETTER, SETTER)
            case v: ScValue if v.hasAnnotation("scala.reflect.BooleanBeanProperty").isDefined => Array(IS_GETTER)
            case v: ScVariable if v.hasAnnotation("scala.reflect.BooleanBeanProperty").isDefined => Array(IS_GETTER, SETTER)
            case _ => Array.empty
          }
          a.map(t.getTypedDefinitionWrapper(false, false, _, None))
        }
      case _ => Array.empty
    }
  }
}