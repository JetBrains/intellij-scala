package org.jetbrains.plugins.scala.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

private final class ConfigureIncrementalCompilerStartupActivity extends StartupActivity.DumbAware {

  override def runActivity(project: Project): Unit = {
    project.subscribeToModuleRootChanged() { _ =>
      if (!project.isDisposed) {
        var scalaSdkFound = false
        var kotlinLibraryFound = false

        OrderEnumerator.orderEntries(project).librariesOnly().recursively().forEachLibrary { lib =>
          if (!scalaSdkFound && lib.isScalaSdk) {
            scalaSdkFound = true
          } else if (isKotlinRuntimeOrLibrary(lib)) {
            kotlinLibraryFound = true
          }

          // Stop processing libraries only if both a Scala SDK and a Kotlin library have been found.
          !scalaSdkFound || !kotlinLibraryFound
        }

        if (scalaSdkFound && kotlinLibraryFound) {
          ScalaCompilerConfiguration.instanceIn(project).incrementalityType = IncrementalityType.IDEA
        }
      }
    }
  }

  /**
   * This is a best-effort guess if a project has Kotlin configured, by applying some heuristics to the project
   * libraries.
   */
  private def isKotlinRuntimeOrLibrary(library: Library): Boolean = library match {
    case lib if lib.getName ne null =>
      // In a JPS project, configuring the Kotlin language results in a special library called "KotlinJavaRuntime"
      // being added by IDEA to the project. If we detect this library, the project definitely has Kotlin enabled.
      // In Maven projects, the library kind doesn't seem to be "kotlin.<platform>", but instead a library of kind
      // Maven. In this case, if one of the libraries is the Kotlin standard library, then it is safer to assume that
      // the project has the Kotlin programming language configured.
      val name = lib.getName
      name == "KotlinJavaRuntime" || name.contains("kotlin-stdlib")
    case lib: LibraryEx =>
      // In Gradle projects, configuring the Kotlin language adds libraries with the special Kotlin kinds
      // (depending on the platform).
      lib.getKind match {
        case null => false
        case kind => kotlinLibraryKinds(kind.getKindId)
      }
    case _ => false
  }

  /**
   * Taken from "KotlinLibraryKind.kt". The special Kotlin platform dependent library kinds.
   */
  private val kotlinLibraryKinds: Set[String] = Set("kotlin.common", "kotlin.jvm", "kotlin.js", "kotlin.native")
}
