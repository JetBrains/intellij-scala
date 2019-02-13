package org.jetbrains.plugins.scala
package lang
package refactoring

import com.intellij.openapi.util.TextRange

case class Association(path: dependency.Path,
                       var range: TextRange)