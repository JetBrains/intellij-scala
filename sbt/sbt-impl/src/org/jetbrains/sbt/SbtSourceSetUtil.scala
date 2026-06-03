package org.jetbrains.sbt

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.util.SbtModuleType
import org.jetbrains.sbt.project.SourceSetType

object SbtSourceSetUtil {
  implicit class SbtSourceSetModuleExt(private val module: Module) extends AnyVal {

    /**
     * Determines if a module is a test module by checking the external module type and module name suffix.
     * If the external module type is <code>null</code>, it returns <code>false</code>.
     */
    def isTest: Boolean = is(SourceSetType.Test)

    /**
     * Determines if a module is a main module by checking the external module type and module name suffix.
     * If the external module type is <code>null</code>, it returns <code>false</code>.
     */
    def isMain: Boolean = is(SourceSetType.Main)

    def isSbtSourceSetModule: Boolean = {
      val externalModuleType = ExternalSystemApiUtil.getExternalModuleType(module)
      externalModuleType == SbtModuleType.sbtSourceSetModuleType
    }

    private def is(sourceSetType: SourceSetType): Boolean =
      isSbtSourceSetModule && {
        // NOTE!!
        // In some edge cases, the source set module name may also end with a number e.g., .main~<number>,
        // so we should handle this scenario as well
        val pattern = s"^.*\\.$sourceSetType(~\\d+)?$$".r
        pattern.matches(module.getName)
      }
  }
}
