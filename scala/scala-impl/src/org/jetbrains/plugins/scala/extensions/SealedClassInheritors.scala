package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

import scala.jdk.CollectionConverters.CollectionHasAsScala

object SealedClassInheritors {
  def unapply(cls: ScTypeDefinition): Option[Seq[ScTypeDefinition]] = Option.when(cls.isSealed) {
    ClassInheritorsSearch.search(cls, new LocalSearchScope(cls.getContainingFile), true)
      .findAll()
      .asScala
      .toSeq
      .filterByType[ScTypeDefinition]
  }
}
