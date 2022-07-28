package org.jetbrains.plugins.scala
package gotoclass

import com.intellij.navigation.{GotoClassContributor, NavigationItem}
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import java.util
import scala.collection.mutable.ArrayBuffer

class ScalaGoToSymbolContributor extends GotoClassContributor {

  override def getNames(project: Project, includeNonProjectItems: Boolean): Array[String] = {
    val stubIndex: StubIndex = StubIndex.getInstance
    val keys: util.ArrayList[String] = new util.ArrayList[String]
    keys.addAll(stubIndex.getAllKeys(METHOD_NAME_KEY, project))
    keys.addAll(stubIndex.getAllKeys(PROPERTY_NAME_KEY, project))
    keys.addAll(stubIndex.getAllKeys(CLASS_PARAMETER_NAME_KEY, project))
    keys.addAll(stubIndex.getAllKeys(TYPE_ALIAS_NAME_KEY, project))
    keys.addAll(stubIndex.getAllKeys(NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY, project))
    keys.toArray(new Array[String](keys.size))
  }

  override def getItemsByName(name: String, pattern: String, project: Project, includeNonProjectItems: Boolean): Array[NavigationItem] = {
    implicit val p: Project = project

    val searchAll: Boolean = ScalaProjectSettings.getInstance(project).isSearchAllSymbols
    val scope =
      if (includeNonProjectItems) GlobalSearchScope.allScope(project)
      else GlobalSearchScope.projectScope(project)

    val cleanName: String = ScalaNamesUtil.cleanFqn(name)
    val methods = METHOD_NAME_KEY.elements(cleanName, scope)
    val typeAliases = TYPE_ALIAS_NAME_KEY.elements(cleanName, scope)
    val items = ArrayBuffer.empty[NavigationItem]
    if (searchAll) {
      items ++= methods
      items ++= typeAliases
    }
    else {
      items ++= methods.filter(isNonLocal)
      items ++= typeAliases.filter(isNonLocal)
    }
    for (property <- PROPERTY_NAME_KEY.elements(cleanName, scope)) {
      if (isNonLocal(property) || searchAll) {
        val elems = property.declaredElementsArray
        for (elem <- elems) {
          elem match {
            case item: NavigationItem =>
              val navigationItemName: String = ScalaNamesUtil.scalaName(elem)
              if (ScalaNamesUtil.equivalentFqn(name, navigationItemName))
                items += item
            case _ =>
          }
        }
      }
    }
    for (clazz <- NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY.elements(cleanName, scope)) {
      if (isNonLocal(clazz) || searchAll) {
        val navigationItemName: String = ScalaNamesUtil.scalaName(clazz)
        if (ScalaNamesUtil.equivalentFqn(name, navigationItemName))
          items += clazz
      }
    }
    items ++= CLASS_PARAMETER_NAME_KEY.elements(cleanName, scope)
    items.toArray
  }

  override def getQualifiedName(item: NavigationItem): String = item match {
    case clazz: ScTypeDefinition => clazz.qualifiedName
    case named: ScNamedElement => named.name
    case _ => null
  }

  override def getQualifiedNameSeparator: String = "."

  private def isNonLocal(item: NavigationItem): Boolean = {
    item match {
      case m: ScMember => !m.isLocal
      case _ => false
    }
  }
}