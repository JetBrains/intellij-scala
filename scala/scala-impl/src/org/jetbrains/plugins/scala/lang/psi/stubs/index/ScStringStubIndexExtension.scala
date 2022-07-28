package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import org.jetbrains.plugins.scala.finder.ScalaFilterScope

import java.{util => ju}

abstract class ScStringStubIndexExtension[E <: PsiElement] extends StringStubIndexExtension[E] {

  override final def get(key: String, project: Project, scope: GlobalSearchScope): ju.Collection[E] =
    super.get(key, project, ScalaFilterScope(scope)(project))
}
