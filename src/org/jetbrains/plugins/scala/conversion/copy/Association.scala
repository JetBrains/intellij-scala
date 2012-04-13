package org.jetbrains.plugins.scala
package conversion.copy

import org.jetbrains.plugins.scala.lang.dependency.{DependencyKind, Path}
import com.intellij.openapi.util.TextRange

/**
 * Pavel Fatin
 */

case class Association(kind: DependencyKind, range: TextRange, path: Path)