package org.jetbrains.plugins.scala.gotoclass

import com.intellij.navigation.{GotoClassContributor, NavigationItem}
import com.intellij.openapi.project.{PossiblyDumbAware, Project}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.{DumbModeAccessType, FileBasedIndex}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

class ScalaGoToClassContributor extends GotoClassContributor with PossiblyDumbAware {

  import ScalaIndexKeys._

  override def getNames(project: Project, includeNonProjectItems: Boolean): Array[String] = {
    implicit val projectCopy: Project = project

    DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE.ignoreDumbMode { () =>
      (NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY.allKeys ++ PACKAGE_OBJECT_SHORT_NAME_KEY.allKeys).toArray
    }
  }

  override def getItemsByName(name: String, pattern: String, project: Project, includeNonProjectItems: Boolean): Array[NavigationItem] = {
    val scope = if (includeNonProjectItems) GlobalSearchScope.allScope(project) else GlobalSearchScope.projectScope(project)
    val cleanName = ScalaNamesUtil.cleanFqn(name)

    implicit val projectCopy: Project = project

    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode { () =>
      val classes = NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY.elements(cleanName, scope)
      val packageObjects = PACKAGE_OBJECT_SHORT_NAME_KEY.elements(cleanName, scope)
      (classes ++ packageObjects).toArray[NavigationItem]
    }
  }

  override def getQualifiedName(item: NavigationItem): String = item match {
    case t: ScTypeDefinition => t.qualifiedName
    case _ => null
  }

  override def getQualifiedNameSeparator: String = "."

  override def isDumbAware: Boolean = FileBasedIndex.isIndexAccessDuringDumbModeEnabled
}
