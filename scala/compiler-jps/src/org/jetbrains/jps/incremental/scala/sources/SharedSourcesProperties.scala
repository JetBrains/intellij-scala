package org.jetbrains.jps.incremental.scala.sources

import org.jetbrains.jps.model.JpsDummyElement
import org.jetbrains.jps.model.ex.JpsElementBase

class SharedSourcesProperties(val ownerModuleNames: Seq[String]) extends JpsElementBase[SharedSourcesProperties] with JpsDummyElement