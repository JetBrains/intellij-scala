package org.jetbrains.plugins.scala.projectView

import com.intellij.ide.projectView.ViewSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt.getNameWithoutExtension
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.clean

sealed trait FileKind {
  protected val delegate: ScTypeDefinition

  final type MyIconableNode = Node with IconableNode

  def node(implicit project: Project, settings: ViewSettings): Option[MyIconableNode]
}

object FileKind {
  import org.jetbrains.plugins.scala.extensions._

  @deprecated("Use FileKind.getForFile directly")
  @Deprecated
  @ScheduledForRemoval(inVersion = "2023.2")
  def unapply(file: ScalaFile): Option[FileKind] = getForFile(file)

  def getForFile(file: ScalaFile): Option[FileKind] = {
    val fileName = clean(getNameWithoutExtension(file.name))

    def matchesFileName(definition: ScTypeDefinition): Boolean =
      clean(definition.name) == fileName

    def bothMatchFileName(first: ScTypeDefinition, second: ScTypeDefinition): Boolean =
      first.name == second.name && clean(first.name) == fileName

    val members = file.members
    val typeDefinitions = members.filterByType[ScTypeDefinition]
    val hasTopLevelNonTypeDefinitions = typeDefinitions.size != members.size
    if (hasTopLevelNonTypeDefinitions)
      None
    else
      typeDefinitions.toList match {
        case (definition: ScObject) :: Nil if definition.isPackageObject =>
          Some(PackageObject(definition))
        case definition :: Nil if matchesFileName(definition) =>
          Some(TypeDefinition(definition))
        case (first@(_: ScClass | _: ScTrait | _: ScEnum)) :: (second: ScObject) :: Nil if bothMatchFileName(first, second) =>
          Some(CompanionsFileKind(first, second))
        case (first: ScObject) :: (second@(_: ScClass | _: ScTrait | _: ScEnum)) :: Nil if bothMatchFileName(first, second) =>
          Some(CompanionsFileKind(second, first))
        case _ => None
      }
  }

  private case class PackageObject(
    override protected val delegate: ScObject
  ) extends FileKind {

    override def node(implicit project: Project, settings: ViewSettings): Option[MyIconableNode] =
      Some(new PackageObjectNode(delegate))
  }

  private case class TypeDefinition(
    override protected val delegate: ScTypeDefinition
  ) extends FileKind {

    override def node(implicit project: Project, settings: ViewSettings): Option[MyIconableNode] =
      Some(new TypeDefinitionNode(delegate))
  }

  private final case class CompanionsFileKind(
    override protected val delegate: ScTypeDefinition,
    protected val companionObject: ScObject
  ) extends FileKind {

    override def node(implicit project: Project, settings: ViewSettings): Option[MyIconableNode] =
      if (settings != null && settings.isShowMembers)
        None
      else
        Some(new ScalaCompanionsFileNode(project, delegate, companionObject, settings))
  }
}