package org.jetbrains.plugins.scala.config

import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.psi.PsiElement

/**
 * @author Alefas
 * @since 14.05.12
 */

object ScalaVersionUtil {
  val SCALA_2_7 = "2.7"
  val SCALA_2_8 = "2.8"
  val SCALA_2_9 = "2.9"
  val SCALA_2_10 = "2.10"
  val SCALA_2_11 = "2.11"

  def isGeneric(element: PsiElement, defaultValue: Boolean, versionText: String*): Boolean = {
    val module: Module = ModuleUtilCore.findModuleForPsiElement(element)
    if (module == null) return defaultValue
    ScalaFacet.findIn(module).map(facet => {
      val version = facet.version
      if (versionText.exists(version.startsWith)) true
      else false
    }).getOrElse(defaultValue)
  }
}
