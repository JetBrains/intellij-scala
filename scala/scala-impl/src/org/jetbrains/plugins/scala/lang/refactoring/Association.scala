package org.jetbrains.plugins.scala.lang.refactoring

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.dependency.DependencyPath

case class Association(path: DependencyPath, var range: TextRange)