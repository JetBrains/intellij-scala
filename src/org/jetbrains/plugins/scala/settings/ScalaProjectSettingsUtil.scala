package org.jetbrains.plugins.scala.settings

import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
 * @author Alefas
 * @since 25.05.12
 */

object ScalaProjectSettingsUtil {
  def isValidPackage(packageName: String): Boolean = {
    if (packageName.trim.startsWith(".") || packageName.trim.endsWith(".")) return false
    val parts = packageName.split('.')
    for (i <- 0 until parts.length) {
      if (!ScalaNamesUtil.isIdentifier(parts(i)) || parts(i).isEmpty) {
        if (i != parts.length - 1 || parts(i) != "_") return false
      }
    }
    true
  }
}
