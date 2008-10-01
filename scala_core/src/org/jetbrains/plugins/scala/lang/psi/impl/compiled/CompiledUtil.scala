package org.jetbrains.plugins.scala.lang.psi.impl.compiled
/**
 * @author ilyas
 */

object CompiledUtil {

  def getSourceFileName(sfn: String): Option[String] = {
    if (sfn != null) {
      if (sfn.endsWith(".scala")) {
        Some(sfn)
      }
      else if (sfn.endsWith(".class")) {
        var i = sfn.indexOf('$')
        if (i < 0) {
          i = sfn.indexOf('.')
          if (i < 0) {
            i = sfn.length
          }
        }
        return Some(sfn.substring(0, i) + ".scala")
      }
    }
    None
  }

}