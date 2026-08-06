package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.structuralsearch.plugin.ui.filters.{FilterAction, FilterProvider}

import java.util

class ScalaFilterProvider extends FilterProvider {
  
  override def getFilters: util.List[FilterAction] = util.List.of()
}
