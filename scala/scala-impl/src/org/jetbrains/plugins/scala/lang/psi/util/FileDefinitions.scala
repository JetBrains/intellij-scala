package org.jetbrains.plugins.scala.lang.psi.util

import com.intellij.openapi.util.io.FileUtilRt.getNameWithoutExtension
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.clean

/**
 * The top-level definitions that make up a whole Scala file: a package object, a single type definition named
 * after the file, or a type definition named after the file together with its companion object.
 *
 * @see [[org.jetbrains.plugins.scala.projectView.FileKind]] for how such files are shown in the project view
 */
sealed trait FileDefinitions

object FileDefinitions {

  final case class PackageObject(definition: ScObject) extends FileDefinitions

  final case class TypeDefinition(definition: ScTypeDefinitionLike) extends FileDefinitions

  final case class Companions(definition: ScTypeDefinitionLike, companionObject: ScObject) extends FileDefinitions

  def getForFile(file: ScalaFile): Option[FileDefinitions] = {
    val fileName = clean(getNameWithoutExtension(file.name))

    def matchesFileName(definition: ScNamedElement): Boolean =
      clean(definition.name) == fileName

    def bothMatchFileName(first: ScNamedElement, second: ScNamedElement): Boolean =
      first.name == second.name && clean(first.name) == fileName

    val members = file.members
    val typeDefinitions = members.collect {
      case td: ScTypeDefinitionLike if td.canHaveCompanion => td
    }
    val hasTopLevelNonTypeDefinitions = typeDefinitions.size != members.size
    if (hasTopLevelNonTypeDefinitions)
      None
    else
      typeDefinitions.toList match {
        case (definition: ScObject) :: Nil if definition.isPackageObject =>
          Some(PackageObject(definition))
        case (definition: ScTypeDefinitionLike) :: Nil if matchesFileName(definition) && definition.canHaveCompanion =>
          Some(TypeDefinition(definition))
        case (first@(_: ScClass | _: ScTrait | _: ScEnum | _: ScTypeAlias)) :: (second: ScObject) :: Nil if bothMatchFileName(first, second) =>
          Some(Companions(first, second))
        case (first: ScObject) :: (second@(_: ScClass | _: ScTrait | _: ScEnum | _: ScTypeAlias)) :: Nil if bothMatchFileName(first, second) =>
          Some(Companions(second, first))
        case _ => None
      }
  }
}
