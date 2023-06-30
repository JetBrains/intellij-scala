package org.jetbrains.plugins.scala.projectView

import com.intellij.ide.projectView.ViewSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.io.FileUtilRt.getNameWithoutExtension
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.clean
import org.jetbrains.plugins.scala.util.BaseIconProvider

import javax.swing.Icon

sealed trait FileKind {
  protected val delegate: ScTypeDefinition

  def node(implicit project: Project, settings: ViewSettings): Option[Node with IconableNode]
}

object FileKind {
  import org.jetbrains.plugins.scala.extensions._
  import org.jetbrains.plugins.scala.icons.Icons._

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
        case (definition: ScObject)                  :: Nil if definition.isPackageObject       => Some(PackageObject(definition))
        case definition                              :: Nil if matchesFileName(definition)      => Some(TypeDefinition(definition))
        case (first: ScClass)  :: (second: ScObject) :: Nil if bothMatchFileName(first, second) => Some(ClassAndCompanionObject(first, second))
        case (first: ScObject) :: (second: ScClass)  :: Nil if bothMatchFileName(first, second) => Some(ClassAndCompanionObject(second, first))
        case (first: ScTrait)  :: (second: ScObject) :: Nil if bothMatchFileName(first, second) => Some(TraitAndCompanionObject(first, second))
        case (first: ScObject) :: (second: ScTrait)  :: Nil if bothMatchFileName(first, second) => Some(TraitAndCompanionObject(second, first))
        case _ => None
      }
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

  private sealed trait CompanionsFileKind extends FileKind with Iconable {
    protected val companionObject: ScObject

    override def node(implicit project: Project, settings: ViewSettings): Option[Node with IconableNode] =
      if (settings != null && settings.isShowMembers)
        None
      else
        Some(new ScalaCompanionsFileNode(project, delegate, settings, this))
  }

  private final case class ClassAndCompanionObject(
    override protected val delegate: ScClass,
    override protected val companionObject: ScObject
  ) extends CompanionsFileKind
    with BaseIconProvider {

    protected override lazy val baseIcon: Icon =
      if (delegate.hasAbstractModifier) ABSTRACT_CLASS_AND_OBJECT
      else CLASS_AND_OBJECT
  }

  private final case class TraitAndCompanionObject(
    override protected val delegate: ScTrait,
    override protected val companionObject: ScObject
  ) extends CompanionsFileKind
    with BaseIconProvider {

    protected override lazy val baseIcon: Icon = TRAIT_AND_OBJECT
  }
}