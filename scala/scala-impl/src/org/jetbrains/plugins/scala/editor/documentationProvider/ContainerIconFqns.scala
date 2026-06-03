package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum, ScObject, ScTrait}

/**
 * @see [[org.jetbrains.plugins.scala.icons.Icons]]
 * @see [[com.intellij.icons.AllIcons]]
 */
private object ContainerIconUtils {

  private val EnumFqn = "org.jetbrains.plugins.scala.icons.Icons.ENUM"
  private val ClassFqn = "org.jetbrains.plugins.scala.icons.Icons.CLASS"
  private val TraitFqn = "org.jetbrains.plugins.scala.icons.Icons.TRAIT"
  private val PackageObjectFqn = "org.jetbrains.plugins.scala.icons.Icons.PACKAGE_OBJECT"
  private val ObjectFqn = "org.jetbrains.plugins.scala.icons.Icons.OBJECT"
  private val PackageFqn = "AllIcons.Nodes.Package"

  def getContainerIconFqn(container: PsiElement): Option[String] =
    container match {
      case _: ScEnum => Some(EnumFqn)
      case _: ScClass => Some(ClassFqn)
      case _: ScTrait => Some(TraitFqn)
      case o: ScObject if o.isPackageObject => Some(PackageObjectFqn)
      case _: ScObject => Some(ObjectFqn)
      case _: ScPackaging => Some(PackageFqn)
      case _ => None
    }
}
