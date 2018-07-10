package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io.File
import scala.collection.Set

final case class IndexerParsingFailure(classes: Set[File])
