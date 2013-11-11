package org.jetbrains.plugins.scala
package config

import com.intellij.psi.PsiElement
import com.intellij.openapi.module.{ModuleUtilCore, Module}
import configuration._

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
    module.scalaSdk.map( sdk => {
      val version = sdk.languageLevel.getName
      if (versionText.exists(version.contains)) true
      else false
    }).getOrElse(defaultValue)
  }
}
