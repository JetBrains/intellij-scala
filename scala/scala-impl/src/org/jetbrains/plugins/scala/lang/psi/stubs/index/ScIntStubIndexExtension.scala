package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.IntStubIndexExtension
import org.jetbrains.plugins.scala.finder.ScalaFilterScope

import java.{util => ju}

abstract class ScIntStubIndexExtension[E <: PsiElement] extends IntStubIndexExtension[E] {

  override final def get(key: Integer, project: Project, scope: GlobalSearchScope): ju.Collection[E] =
    super.get(key, project, ScalaFilterScope(scope)(project))
}
