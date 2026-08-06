package org.jetbrains.plugins.scala.project.maven

import com.intellij.openapi.module.Module
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.testingSupport.TestWorkingDirectoryProvider

class MavenTestWorkingDirectoryProvider extends TestWorkingDirectoryProvider {

  override def getWorkingDirectory(module: Module): Option[String] =
    for {
      manager <- MavenProjectsManager.getInstance(module.getProject).toOption
      mavenProject <- manager.findProject(module).toOption
    } yield mavenProject.getDirectory
}
