package org.jetbrains.sbt.language.utils

import com.intellij.psi.PsiElement

case class DependencyOrRepositoryPlaceInfo(path: String, offset: Int, line: Int, element: PsiElement, affectedProjects: Seq[String])
