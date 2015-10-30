package org.jetbrains.plugins.scala.project.maven

import com.intellij.openapi.module.Module
import org.jetbrains.idea.maven.project.MavenProjectsManager

/**
 * @author Roman.Shein
 * @since 30.10.2015.
 */
class MavenWorkingDirectoryProviderImpl extends MavenWorkingDirectoryProvider {
  override def getWorkingDirectory(module: Module): String =
    if (module == null) {
      null
    } else {
      Option(MavenProjectsManager.getInstance(module.getProject).findProject(module)).map(_.getDirectory).orNull
    }
}
