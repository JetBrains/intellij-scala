package org.jetbrains.plugins.scala.lang.refactoring

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.dependency.DependencyPath

/**
 * @param range range of the reference, relative to the range of the text (e.g., the copied text) <br>
 *              The corresponding reference is supposed to be resolved to the `path`
 *              and be an instance of [[org.jetbrains.plugins.scala.lang.psi.api.base.ScReference]]
 */
case class Association(path: DependencyPath, var range: TextRange)