package org.jetbrains.plugins.scala.project.template

import com.intellij.facet.impl.ui.libraries.LibraryOptionsPanel
import com.intellij.framework.library.FrameworkLibraryVersionFilter
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.project.ScalaLibraryType

trait ScalaSDKStepLike extends PackagePrefixStepLike {

  protected def librariesContainer: LibrariesContainer

  //noinspection ScalaExtractStringToBundle
  @Nls
  protected val scalaSdkLabelText: String = "Scala S\u001BDK:"

  protected lazy val libraryPanel = new LibraryOptionsPanel(
    ScalaLibraryType.Description,
    "",
    FrameworkLibraryVersionFilter.ALL,
    librariesContainer,
    false
  )
}
