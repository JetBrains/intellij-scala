package org.jetbrains.sbt.annotator.dependency

import com.intellij.psi.PsiElement

/**
  * Created by afonichkin on 7/24/17.
  */
case class DependencyPlaceInfo(path: String, offset: Int, line: Int, element: PsiElement, affectedProjects: Seq[String])