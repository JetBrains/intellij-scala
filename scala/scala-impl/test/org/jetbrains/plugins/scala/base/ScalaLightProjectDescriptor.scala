package org.jetbrains.plugins.scala.base

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightProjectDescriptor

/**
 * See other examples:
 *  - [[com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.ProjectDescriptor]]
 *  - [[com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor]]
 */
class ScalaLightProjectDescriptor(private val sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken.DoNotShare) extends LightProjectDescriptor {

  override def setUpProject(project: Project, handler: LightProjectDescriptor.SetupHandler): Unit = {
    super.setUpProject(project, handler)
    val modules = ModuleManager.getInstance(project).getModules
    tuneModule(modules.head, project)
  }

  /** We also pass project because `getProject` in test classes might still be not-initialized (null) */
  def tuneModule(module: Module, project: Project): Unit = ()

  /** see [[com.intellij.testFramework.LightPlatformTestCase.doSetup]] */
  override def equals(obj: Any): Boolean =
    obj match {
      case other: ScalaLightProjectDescriptor =>
        val equals = for {
          id1 <- this.sharedProjectToken.value
          id2 <- other.sharedProjectToken.value
        } yield id1 == id2
        equals.getOrElse(false)
      case _ =>
        super.equals(obj)
    }
}
