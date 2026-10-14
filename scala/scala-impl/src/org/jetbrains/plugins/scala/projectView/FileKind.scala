package org.jetbrains.plugins.scala.projectView

import com.intellij.ide.projectView.ViewSettings
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.util.FileDefinitions

sealed trait FileKind {
  protected val delegate: ScTypeDefinitionLike

  final type MyIconableNode = Node & IconableNode

  private[scala] final def representative: ScTypeDefinitionLike = delegate

  def node(implicit project: Project, settings: ViewSettings): Option[MyIconableNode]
}

object FileKind {

  def getForFile(file: ScalaFile): Option[FileKind] =
    FileDefinitions.getForFile(file).map {
      case FileDefinitions.PackageObject(definition)               => PackageObject(definition)
      case FileDefinitions.TypeDefinition(definition)              => TypeDefinition(definition)
      case FileDefinitions.Companions(definition, companionObject) => CompanionsFileKind(definition, companionObject)
    }

  private case class PackageObject(
    override protected val delegate: ScObject
  ) extends FileKind {

    override def node(implicit project: Project, settings: ViewSettings): Option[MyIconableNode] =
      Some(new PackageObjectNode(delegate))
  }

  private case class TypeDefinition(
    override protected val delegate: ScTypeDefinitionLike
  ) extends FileKind {

    override def node(implicit project: Project, settings: ViewSettings): Option[MyIconableNode] = delegate match {
      case td: ScTypeDefinition => Some(new TypeDefinitionNode(td))
      case td => Some(new NamedElementNode(td))
    }
  }

  private final case class CompanionsFileKind(
    override protected val delegate: ScTypeDefinitionLike,
    protected val companionObject: ScObject
  ) extends FileKind {

    override def node(implicit project: Project, settings: ViewSettings): Option[MyIconableNode] =
      if (settings != null && settings.isShowMembers)
        None
      else
        Some(new ScalaCompanionsFileNode(project, delegate, settings))
  }
}
