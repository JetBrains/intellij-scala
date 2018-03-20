package org.jetbrains.plugins.scala.components.libextensions

import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier.Version
import org.jetbrains.plugins.scala.components.libextensions.api.psi._

class LibraryExtensionsManager(project: Project) extends AbstractProjectComponent(project) {

  def getAvailableLibraries: Seq[LibraryDescriptor] = Seq(
    LibraryDescriptor(
      "shapeless", "", "extensions for shapeless", "org.jetbrains", "3.2.3",
      PluginDescriptor(Version.Snapshot, Version.Snapshot, None,
        inspections = Extension("org.foo.Bar", "Cool inspection", enabled = true):: Nil)
        :: Nil
    )
  )

}

object LibraryExtensionsManager {

  case class DeclaredExtension(xmlKind: String, apiClass: Class[_])

  val availableExtensions: List[DeclaredExtension] = DeclaredExtension("type-transformer", classOf[MacroTypeContributor]) ::
                            DeclaredExtension("expression-transformer", classOf[MacroExprContributor]) ::
                            DeclaredExtension("tmplatedef-transformer", classOf[TypeDefTransformer]) ::
                            DeclaredExtension("synthetic-member-injector", classOf[SyntheticMembersInjector]) ::
                            DeclaredExtension("inspection", classOf[Inspection]) ::
                            DeclaredExtension("intention", classOf[Intention]) ::
                            DeclaredExtension("migrator", classOf[Migrator]) :: Nil

  def getInstance(project: Project): LibraryExtensionsManager = project.getComponent(classOf[LibraryExtensionsManager])

}
