package org.jetbrains.plugins.scala
package projectView

import com.intellij.ide.projectView.ViewSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt.getNameWithoutExtension
import javax.swing.Icon
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.clean

sealed trait FileKind {

  protected val definition: ScTypeDefinition

  def node(implicit project: Project, settings: ViewSettings): Option[Node with IconProviderNode]
}

object FileKind {

  import extensions._
  import icons.Icons._

  def unapply(file: ScalaFile): Option[FileKind] = Option {
    file.typeDefinitions.toList match {
      case (definition: ScObject) :: Nil if definition.isPackageObject => PackageObject(definition)
      case firstDefinition :: tail if matchesFileName(firstDefinition) =>
        tail match {
          case Nil => TypeDefinition(firstDefinition)
          case secondDefinition :: Nil if firstDefinition.name == secondDefinition.name =>
            (firstDefinition, secondDefinition) match {
              case (firstDefinition: ScClass, companionObject: ScObject) => ClassAndCompanionObject(firstDefinition, companionObject)
              case (companionObject: ScObject, secondDefinition: ScClass) => ClassAndCompanionObject(secondDefinition, companionObject)
              case (firstDefinition: ScTrait, companionObject: ScObject) => TraitAndCompanionObject(firstDefinition, companionObject)
              case (companionObject: ScObject, secondDefinition: ScTrait) => TraitAndCompanionObject(secondDefinition, companionObject)
              case _ => null
            }
          case _ => null
        }
      case _ => null
    }
  }

  private def matchesFileName(definition: ScTypeDefinition): Boolean = {
    val cleanDefinitionName = clean(definition.name)

    definition.containingFile.iterator
      .map(_.getName)
      .map(getNameWithoutExtension)
      .map(clean)
      .forall(_ == cleanDefinitionName)
  }

  private sealed trait SingleDefinition extends FileKind

  private case class PackageObject(override protected val definition: ScObject) extends SingleDefinition {

    override def node(implicit project: Project, settings: ViewSettings) =
      Some(new PackageObjectNode(definition))
  }

  private case class TypeDefinition(override protected val definition: ScTypeDefinition) extends SingleDefinition {

    override def node(implicit project: Project, settings: ViewSettings) =
      Some(new TypeDefinitionNode(definition))
  }

  private sealed trait PairedDefinition extends FileKind {

    protected val companionObject: ScObject

    protected def icon(flags: Int): Icon

    override def node(implicit project: Project, settings: ViewSettings): Option[CustomDefinitionNode] =
      if (settings != null && settings.isShowMembers)
        None
      else
        Some {
          new CustomDefinitionNode(definition) {
            override def icon(flags: Int): Icon = PairedDefinition.this.icon(flags)
          }
        }
  }

  private case class ClassAndCompanionObject(override protected val definition: ScClass,
                                             override protected val companionObject: ScObject) extends PairedDefinition {

    override def icon(flags: Int): Icon = {
      val baseIcon = if (definition.hasAbstractModifier) ABSTRACT_CLASS_AND_OBJECT else CLASS_AND_OBJECT
      definition.decorate(baseIcon, flags)
    }
  }

  private case class TraitAndCompanionObject(override protected val definition: ScTrait,
                                             override protected val companionObject: ScObject) extends PairedDefinition {

    override def icon(flags: Int): Icon = TRAIT_AND_OBJECT
  }
}