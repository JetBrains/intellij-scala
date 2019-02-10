package org.jetbrains.plugins.scala
package conversion
package copy

import org.jetbrains.plugins.scala.lang.dependency.{DependencyKind, Path}

/**
  * Pavel Fatin
  */
case class AssociationHelper(kind: DependencyKind,
                             itype: ast.IntermediateNode,
                             path: Path)