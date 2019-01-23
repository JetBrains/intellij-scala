package org.jetbrains.plugins.scala.project

import java.util.Collections

import com.intellij.ProjectTopics
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.{ModuleListener, Project}
import com.intellij.openapi.roots._
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.settings.ZincConfiguration

object CompileToJarComponent {
  private val ProductionOutputJarLibName = "compile-to-jar-output"
  private val TestOutputJarLibName = "compile-to-jar-output-test"
  private val LibraryNames = Set(ProductionOutputJarLibName, TestOutputJarLibName)

  def getInstance(project: Project): CompileToJarComponent = {
    project.getComponent(classOf[CompileToJarComponent])
  }

}

class CompileToJarComponent(project: Project) extends ProjectComponent {
  import CompileToJarComponent._

  private val connection = project.getMessageBus.connect()

  override def projectOpened(): Unit = {
    registerHookToUpdateNewModules()
    configureModulesOnStartup()
  }

  private def configureModulesOnStartup(): Unit = {
    if (compileToJar && notConfigured) {
      addOutputJarsAsDependencies()
    }
  }

  private def registerHookToUpdateNewModules(): Unit = {
    connection.subscribe(ProjectTopics.MODULES, new ModuleListener {
      override def moduleAdded(project: Project, module: Module): Unit = {
        if (module.hasScala && compileToJar) {
          addOutputJarsAsDependencies(module)
        }
      }
    })
  }

  private def notConfigured: Boolean = {
    project.anyScalaModule.exists { module =>
      val libraryNames = module.module.libraries.map(_.getName)
      !LibraryNames.forall(libraryNames.contains)
    }
  }

  override def projectClosed() {
    connection.disconnect()
  }

  private def compileToJar: Boolean = ZincConfiguration.instanceIn(project).compileToJar

  def adjustClasspath(compileToJar: Boolean): Unit = {
    if (compileToJar) {
      addOutputJarsAsDependencies()
    } else {
      removeOutputJarDependencies()
    }
  }

  private def addOutputJarsAsDependencies(): Unit = {
    project.modulesWithScala.foreach(addOutputJarsAsDependencies)
  }

  private def addOutputJarsAsDependencies(module: Module): Unit = {
    val currentEntries: Set[String] = {
      val orderEntries = ModuleRootManager.getInstance(module).getOrderEntries
      orderEntries.map(_.getPresentableName)(collection.breakOut)
    }

    def addMissingClasspathEntry(name: String, url: String, scope: DependencyScope): Unit = {
      if (!currentEntries.contains(name)) {
        val missingJar = Option(url).map(_ + ".jar")
        missingJar.foreach(url => addModuleLibrary(module, name, url, scope))
      }
    }

    val compilerExtension = CompilerModuleExtension.getInstance(module)
    addMissingClasspathEntry(ProductionOutputJarLibName, compilerExtension.getCompilerOutputUrl, DependencyScope.COMPILE)
    addMissingClasspathEntry(TestOutputJarLibName, compilerExtension.getCompilerOutputUrlForTests, DependencyScope.TEST)
  }

  private def removeOutputJarDependencies(): Unit = {
    project.modulesWithScala.foreach { module =>
      removeOrderEntries(module, entry => LibraryNames.contains(entry.getPresentableName))
    }
  }

  private def addModuleLibrary(module: Module, name: String, url: String, scope: DependencyScope): Unit = {
    val classes = Collections.singletonList(url)
    val sources = Collections.emptyList[String]
    val excludedRoots = Collections.emptyList[String]
    val exported = true
    inWriteAction {
      ModuleRootModificationUtil.addModuleLibrary(module, name, classes, sources, excludedRoots, scope, exported)
    }
  }

  private def removeOrderEntries(module: Module, shouldRemove: OrderEntry => Boolean): Unit = {
    inWriteAction {
      ModuleRootModificationUtil.updateModel(module, { model =>
        val toRemove = model.getOrderEntries.filter(shouldRemove)
        toRemove.foreach(model.removeOrderEntry)
      })
    }
  }

}
