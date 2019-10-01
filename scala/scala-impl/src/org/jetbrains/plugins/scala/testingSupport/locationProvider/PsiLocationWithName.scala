package org.jetbrains.plugins.scala.testingSupport.locationProvider

import com.intellij.execution.PsiLocation
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

case class PsiLocationWithName[T <: PsiElement](
  project: Project,
  element: T,
  name: String
) extends PsiLocation[T](project, element)