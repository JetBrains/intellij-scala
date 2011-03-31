package org.jetbrains.plugins.scala
package lang
package psi
package api

import com.intellij.psi.search.GlobalSearchScope
import toplevel.typedef.ScTypeDefinition

trait ScPackageLike {
  def findPackageObject(scope: GlobalSearchScope): Option[ScTypeDefinition]
}