package org.jetbrains.plugins.scala
package lang.formatting.automatic.action

import com.intellij.openapi.actionSystem.{CommonDataKeys, DataContext, AnActionEvent, AnAction}
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import java.nio.file.Paths
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.serialization.ExampleBase
import com.intellij.openapi.project.Project

/**
 * Created by Roman.Shein on 14.07.2014.
 */
class CreateExampleBaseAction extends AnAction {

  private var exampleProjectsPath = Paths.get("C:\\statProject\\") //TODO: replace this with something more thoughtful
  private var storePath = Paths.get("testBase") //TODO: replace this with something more thoughtful
  private var examplesPerRule = 200 //TODO: replace this with something more thoughtful

  override def actionPerformed(e: AnActionEvent) {
    println("build base action triggered")
    val dataContext: DataContext = e.getDataContext
    val project: Project = CommonDataKeys.PROJECT.getData(dataContext)
    if (project == null) {
      return
    }
    ExampleBase.buildAndStore(exampleProjectsPath, storePath, examplesPerRule, project)
    println("base built")
  }

  override def update(e: AnActionEvent) {
    ScalaActionUtil enableAndShowIfInScalaFile e
  }
}
