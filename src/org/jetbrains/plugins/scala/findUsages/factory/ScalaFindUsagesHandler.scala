package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.find.findUsages.FindUsagesHandler
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import com.intellij.psi.{PsiNamedElement, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScVariable, ScValue}
import java.util.{Collection, Collections, HashSet, Set}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.08.2009
 */

class ScalaFindUsagesHandler(element: PsiElement) extends {
    val replacedElement: PsiElement = {
      element match {
        case f: FakePsiMethod => f.navElement
        case _ => element
      }
    }
  } with FindUsagesHandler(replacedElement) {
  override def getStringsToSearch(element: PsiElement): Collection[String] = {
    val result: Set[String] = new HashSet[String]()
    replacedElement match {
      case named: PsiNamedElement =>
        val name = named.getName
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
        result
      case _ => Collections.singleton(element.getText)
    }
  }

  override def getSecondaryElements: Array[PsiElement] = {
    replacedElement match {
      case t: ScTypedDefinition => t.getBeanMethods.toArray
      case _ => Array.empty
    }
  }
}