package org.jetbrains.plugins.scala.base.libraryLoaders

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.libraries.{Library, LibraryTable, LibraryTablesRegistrar}
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.{ObjectExt, inWriteAction}
import org.jetbrains.plugins.scala.project.{ReplClasspath, ScalaLibraryProperties, ScalaLibraryType}

import java.{util => ju}

/**
 * This loader creates a lightweight scala sdk.
 * It can be useful when you just want to tell that a module has Scala SDK with some version
 * (e.g. in order Scala 3 files are properly parsed)
 * and when you do not need everything else (compiler classpath, sources)
 */
final class MockScalaSDKLoader extends LibraryLoader {

  override def init(implicit module: Module, version: ScalaVersion): Unit = {
    val scalaSdkName = s"mock-scala-sdk-${version.minor}"

    val library = getOrCreateNewLibraryWithNameInProject(module, scalaSdkName)

    inWriteAction {
      val properties = ScalaLibraryProperties(Some(version.minor), Seq.empty, Seq.empty, None, ReplClasspath.Bundled)

      val editor = new ExistingLibraryEditor(library, null)
      editor.setType(ScalaLibraryType())
      editor.setProperties(properties)
      editor.commit()
    }

    // Use ModuleRootModificationUtil so tests that watch model counters observe the SDK entry addition
    // We need to do it outside write action as this calls `invokeAndWait` under the hood which is not allowed with `inWriteAction`
    ModuleRootModificationUtil.updateModel(module, model => {
      model.addLibraryEntry(library)
    })
  }

  private def getOrCreateNewLibraryWithNameInProject(module: Module, scalaSdkName: String): Library = {
    val projectLibraryTable = LibraryTablesRegistrar.getInstance.getLibraryTable(module.getProject)
    val existingLibrary = projectLibraryTable.getLibraryByName(scalaSdkName).toOption
    existingLibrary.getOrElse(addNewDummyProjectLibrary(module, scalaSdkName))
  }

  private def addNewDummyProjectLibrary(module: Module, scalaSdkName: String): Library =
    PsiTestUtil.addProjectLibrary(module, scalaSdkName, ju.List.of(), ju.List.of())
}
