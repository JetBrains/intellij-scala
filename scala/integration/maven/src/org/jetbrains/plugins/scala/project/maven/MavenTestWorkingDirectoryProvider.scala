package org.jetbrains.plugins.scala.project.maven

import com.intellij.openapi.module.Module
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.plugins.scala.testingSupport.TestWorkingDirectoryProvider

/**
 * @author Roman.Shein
 * @since 30.10.2015.
 */
class MavenTestWorkingDirectoryProvider extends TestWorkingDirectoryProvider {
  override def getWorkingDirectory(module: Module): String =
    if (module == null) {
      null
    } else {
      Option(MavenProjectsManager.getInstance(module.getProject).findProject(module)).map(_.getDirectory).orNull
    }
}
