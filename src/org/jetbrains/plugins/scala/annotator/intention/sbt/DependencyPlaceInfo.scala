package org.jetbrains.plugins.scala.annotator.intention.sbt

import com.intellij.psi.PsiElement

/**
  * Created by afonichkin on 7/24/17.
  */
case class DependencyPlaceInfo(path: String, line: Int, element: PsiElement, affectedProjects: Seq[String])