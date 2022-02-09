package org.jetbrains.plugins.scala.util

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.nameContext
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods.{isBeanProperty, isBooleanBeanProperty}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}

import java.util

object ScalaNamesUtil {
  def getNamesOf(element: PsiElement): util.Collection[String] = {
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
}
