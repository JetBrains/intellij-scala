package org.jetbrains.plugins.scala.gotoclass

import java.util

import com.intellij.navigation.{GotoClassContributor, NavigationItem}
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.{StubIndex, StubIndexKey}
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.finder.ScalaFilterScope
import org.jetbrains.plugins.scala.gotoclass.ScalaGoToSymbolContributor.{getElements, isNonLocal}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.{CLASS_PARAMETER_NAME_KEY, METHOD_NAME_KEY, NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY, PROPERTY_NAME_KEY, TYPE_ALIAS_NAME_KEY}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

/**
 * User: Alexander Podkhalyuzin
 * Date: 14.10.2008
 */
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
    val searchAll: Boolean = ScalaProjectSettings.getInstance(project).isSearchAllSymbols
    val scope: GlobalSearchScope =
      if (includeNonProjectItems) GlobalSearchScope.allScope(project)
      else GlobalSearchScope.projectScope(project)

    val cleanName: String = ScalaNamesUtil.cleanFqn(name)
    val methods = getElements(METHOD_NAME_KEY, cleanName, project, scope, classOf[ScFunction])
    val typeAliases = getElements(TYPE_ALIAS_NAME_KEY, cleanName, project, scope, classOf[ScTypeAlias])
    val items = ArrayBuffer.empty[NavigationItem]
    if (searchAll) {
      items ++= methods
      items ++= typeAliases
    }
    else {
      items ++= methods.filter(isNonLocal)
      items ++= typeAliases.filter(isNonLocal)
    }
    for (property <- getElements(PROPERTY_NAME_KEY, cleanName, project, scope, classOf[ScValueOrVariable])) {
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
    for (clazz <- getElements(NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY, cleanName, project, scope, classOf[PsiClass])) {
      if (isNonLocal(clazz) || searchAll) {
        val navigationItemName: String = ScalaNamesUtil.scalaName(clazz)
        if (ScalaNamesUtil.equivalentFqn(name, navigationItemName))
          items += clazz
      }
    }
    items ++= getElements(CLASS_PARAMETER_NAME_KEY, cleanName, project, scope, classOf[ScClassParameter])
    items.toArray
  }

  override def getQualifiedName(item: NavigationItem): String = item match {
    case clazz: ScTypeDefinition => clazz.qualifiedName
    case named: ScNamedElement => named.name
    case _ => null
  }

  override def getQualifiedNameSeparator: String = "."
}

object ScalaGoToSymbolContributor {
  private def isNonLocal(item: NavigationItem): Boolean = {
    item match {
      case _ childOf (_: ScTemplateBody | _: ScEarlyDefinitions) => true
      case _ => false
    }
  }

  private def getElements[T <: PsiElement](indexKey: StubIndexKey[String, T],
                                           cleanName: String,
                                           project: Project,
                                           scope: GlobalSearchScope,
                                           requiredClass: Class[T]): Iterable[T] =
    StubIndex.getElements(indexKey, cleanName, project, new ScalaFilterScope(scope, project), requiredClass).asScala
}