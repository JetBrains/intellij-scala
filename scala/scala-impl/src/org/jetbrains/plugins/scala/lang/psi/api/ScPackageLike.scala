package org.jetbrains.plugins.scala
package lang
package psi
package api

import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

trait ScPackageLike extends PsiElement {
  def findPackageObject(scope: GlobalSearchScope): Option[ScTypeDefinition]

  def parentScalaPackage: Option[ScPackageLike]
}