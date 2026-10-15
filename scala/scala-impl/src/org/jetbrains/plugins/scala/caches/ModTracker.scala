package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.{ModificationTracker, SimpleModificationTracker}
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.ScalaFileLocation

object ModTracker {

  /**
   * Use for hot methods: it has minimal overhead, but updates on every change of scala psi in any project<br>
   * This tracker is incremented on changes in both physical and non-physical elements.
   *
   * PsiModificationTracker is not an option, because it
   *  - requires finding project and project service first
   *  - doesn't work for non-physical elements
   *
   * @see https://youtrack.jetbrains.com/issue/SCL-11651/Minimize-usage-of-psi-operations-during-type-inference
   */
  val anyScalaPsiChange: SimpleModificationTracker = new ScalaModificationTracker("anyScalaPsiChange")

  def physicalPsiChange(project: Project): ModificationTracker =
    PsiModificationTracker.getInstance(project)

  def libraryAware(element: PsiElement): ModificationTracker = {
    val rootManager = ProjectRootManager.getInstance(element.getProject)
    element.getContainingFile match {
      case file: ScalaFile if file.isCompiled && ScalaFileLocation.instance.isInLibrary(file.getVirtualFile, element.getProject) => rootManager
      case _: ClsFileImpl => rootManager
      case _ => BlockModificationTracker(element)
    }
  }

}
