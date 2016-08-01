package org.jetbrains.sbt.resolvers

import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project

/**
  * @author Mikhail Mutcianko
  * @since 26.07.16
  */
class SbtResolversManager(val project: Project) extends ProjectComponent {
  override def projectClosed(): Unit = ()

  override def projectOpened(): Unit = ()

  override def initComponent(): Unit = ()

  override def disposeComponent(): Unit = ()

  override def getComponentName: String = "SbtResolversManager"

  def search(groupId: String = "", artifactId: String = "", version: String = ""): Seq[String] = ???

  def updateWithProgress(resolvers: Seq[SbtResolver]) = {
    implicit val p = project
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Updating indexes") {
      override def run(indicator: ProgressIndicator): Unit = {
        indicator.setIndeterminate(true)
        resolvers.foreach {
          res =>
            indicator.setText(res.root)
            res.getIndex.doUpdate(Some(indicator))
        }
      }
    })
  }

}

object SbtResolversManager {
  def getInstance(project: Project): SbtResolversManager = project.getComponent(classOf[SbtResolversManager])
}
