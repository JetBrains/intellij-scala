package org.jetbrains.plugins.scala
package projectView

import com.intellij.ide.projectView.{PresentationData, ViewSettings}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.io.FileUtilRt.getNameWithoutExtension
import javax.swing.Icon
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.clean
import org.jetbrains.plugins.scala.util.BaseIconProvider

sealed trait FileKind {

  protected val delegate: ScTypeDefinition

  def node(implicit project: Project, settings: ViewSettings): Option[Node with IconableNode]
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

  private case class PackageObject(override protected val delegate: ScObject) extends SingleDefinition {

    override def node(implicit project: Project, settings: ViewSettings): Option[Node with IconableNode] =
      Some(new PackageObjectNode(delegate))
  }

  private case class TypeDefinition(override protected val delegate: ScTypeDefinition) extends SingleDefinition {

    override def node(implicit project: Project, settings: ViewSettings): Option[Node with IconableNode] =
      Some(new TypeDefinitionNode(delegate))
  }

  private sealed trait PairedDefinition extends FileKind with Iconable {

    protected val companionObject: ScObject

    override def node(implicit project: Project, settings: ViewSettings): Option[CustomDefinitionNode] =
      if (settings != null && settings.isShowMembers) {
        None
      } else {
        class LeafNode extends CustomDefinitionNode(delegate) {

          override def getIcon(flags: Int): Icon = PairedDefinition.this.getIcon(flags)

          override def isAlwaysLeaf: Boolean = true

          //noinspection TypeAnnotation
          override def getChildrenImpl = emptyNodesList

          override def updateImpl(data: PresentationData): Unit = {
            super.updateImpl(data)
            setIcon(data)
          }
        }

        Some(new LeafNode)
      }
  }

  private case class ClassAndCompanionObject(override protected val delegate: ScClass,
                                             override protected val companionObject: ScObject)
    extends PairedDefinition with BaseIconProvider {

    protected override val baseIcon: Icon =
      if (delegate.hasAbstractModifier) ABSTRACT_CLASS_AND_OBJECT else CLASS_AND_OBJECT
  }

  private case class TraitAndCompanionObject(override protected val delegate: ScTrait,
                                             override protected val companionObject: ScObject) extends PairedDefinition {

    override def getIcon(flags: Int): Icon = TRAIT_AND_OBJECT
  }
}