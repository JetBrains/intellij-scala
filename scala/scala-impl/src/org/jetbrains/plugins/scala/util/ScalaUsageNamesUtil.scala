package org.jetbrains.plugins.scala.util

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.nameContext
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods.{isBeanProperty, isBooleanBeanProperty}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScFunctionDefinition, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}

import java.util

object ScalaUsageNamesUtil {

  val enumSyntheticMethodNames: Set[String] = Set("values", "valueOf", "fromOrdinal")
  val operatorNames: Map[String, String] =
    Map(
      "+" -> "$plus",
      "-" -> "$minus",
      "~" -> "$tilde",
      "==" -> "$eq$eq",
      "<" -> "$less",
      "<=" -> "$less$eq",
      ">" -> "$greater",
      ">=" -> "$greater$eq",
      "!" -> "$bang",
      "%" -> "$percent",
      "^" -> "$up",
      "&" -> "$amp",
      "|" -> "$bar",
      "*" -> "$times",
      "/" -> "$div",
      "\\" -> "$bslash",
      "?" -> "$qmark",
    )

  private def isLiteralIdentifier(id: String) = id.length > 2 && id.startsWith("`") && id.endsWith("`")

  /**
   * When performing operations directly related to usage of some declaration,
   * it's possible that code references to this declaration do not use literally the
   * same name as in the declaration, for example when overriding the assignment
   * operator, or when using the `BeanProperty` annotation.
   *
   * This method provides an exhaustive set of strings which can be used to refer
   * to it in valid code. This includes the name that is used in the declaration itself.
   *
   * If this method is given an element without a name, the element's text is
   * returned.
   *
   * @param element The element of which to return all names of
   * @return Set of all strings that can be used to refer to the element
   */
  def getStringsToSearch(element: PsiElement): util.Collection[String] = {
    val result: util.Set[String] = new util.HashSet[String]()
    element match {
      case enumCase: ScEnumCase =>
        result.add(enumCase.name)
        enumSyntheticMethodNames.foreach(name => result.add(s"${enumCase.enumParent.name}.$name"))
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
      case f: ScFunctionDefinition if f.name.endsWith("_=") =>
        result.add(f.name)
        result.add(f.name.substring(0, f.name.length - 2))
      case f: ScFunctionDefinition if operatorNames.contains(f.name) =>
        result.add(f.name)
        result.add(operatorNames(f.name))
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

    element match {
      case n: ScNamedElement if isLiteralIdentifier(n.name) =>
        result.add(n.name.drop(1).dropRight(1))
      case _ => ()
    }

    result
  }
}
