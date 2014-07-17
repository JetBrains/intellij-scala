package org.jetbrains.plugins.scala
package lang.formatting.automatic.action

import com.intellij.openapi.actionSystem.{CommonDataKeys, DataContext, AnActionEvent, AnAction}
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import com.intellij.openapi.project.Project

/**
 * Created by Roman.Shein on 14.07.2014.
 */
class DeriveSettingsAction extends AnAction {
  override def actionPerformed(e: AnActionEvent) {
    println("derive settings action is performed")
    val dataContext: DataContext = e.getDataContext
    val project: Project = CommonDataKeys.PROJECT.getData(dataContext)
    ScalaBlock.educateFormatter(project)
    ScalaBlock.resetMatcher
  }

  override def update(e: AnActionEvent) {
    ScalaActionUtil enableAndShowIfInScalaFile e
  }
}