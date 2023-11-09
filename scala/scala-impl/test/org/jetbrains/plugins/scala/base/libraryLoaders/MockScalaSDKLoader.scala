package org.jetbrains.plugins.scala.base.libraryLoaders

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.libraries.{Library, LibraryTablesRegistrar}
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.{ObjectExt, inWriteAction}
import org.jetbrains.plugins.scala.project.{ModuleExt, ScalaLibraryProperties, ScalaLibraryType}

import java.{util => ju}

/**
 * This loader creates a lightweight scala sdk.
 * It can be useful when you just want to tell that a module has Scala SDK with some version
 * (e.g. in order Scala 3 files are properly parsed)
 * and when you do not need everything else (compiler classpath, sources)
 */
final class MockScalaSDKLoader() extends LibraryLoader {

  override def init(implicit module: Module, version: ScalaVersion): Unit = {
    val libraryTable = LibraryTablesRegistrar.getInstance.getLibraryTable(module.getProject)
    val scalaSdkName = s"mock-scala-sdk-${version.minor}"

    def createNewLibrary: Library =
      PsiTestUtil.addProjectLibrary(module, scalaSdkName, ju.List.of(), ju.List.of())

    val library =
      libraryTable.getLibraryByName(scalaSdkName)
        .toOption
        .getOrElse(createNewLibrary)

    inWriteAction {
      val properties = ScalaLibraryProperties(Some(version.minor), Seq.empty, Seq.empty)

      val editor = new ExistingLibraryEditor(library, null)
      editor.setType(ScalaLibraryType())
      editor.setProperties(properties)
      editor.commit()

      val model = module.modifiableModel
      model.addLibraryEntry(library)
      model.commit()
    }
  }
}