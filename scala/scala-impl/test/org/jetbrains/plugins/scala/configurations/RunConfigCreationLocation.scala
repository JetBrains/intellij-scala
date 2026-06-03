package org.jetbrains.plugins.scala.configurations

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement

/**
 * Describes from which location configuration is being created.<br>
 * Used to create a [[com.intellij.execution.actions.ConfigurationContext#ConfigurationContext(com.intellij.psi.PsiElement)]]<br>
 * Analog of [[com.intellij.execution.Location]]
 */
sealed trait RunConfigCreationLocation

object RunConfigCreationLocation {
  final case class CaretLocation(fileName: String, line: Int, column: Int) extends RunConfigCreationLocation
  final case class CaretLocation2(virtualVile: VirtualFile, line: Int, column: Int) extends RunConfigCreationLocation
  final case class PackageLocation(packageName: String) extends RunConfigCreationLocation
  final case class ModuleLocation(moduleName: String) extends RunConfigCreationLocation
  final case class PsiElementLocation(psiElement: PsiElement) extends RunConfigCreationLocation
}