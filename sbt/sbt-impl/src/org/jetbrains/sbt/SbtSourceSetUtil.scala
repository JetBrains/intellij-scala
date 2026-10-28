package org.jetbrains.sbt

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.util.SbtModuleType
import org.jetbrains.sbt.project.SourceSetType
import org.jetbrains.sbt.project.settings.DisplayModuleName

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

    /**
     * The name of the sbt project a source set module belongs to, e.g. `root.C` for `root.C.main`; the module's
     * own name for any other module.
     */
    def nameWithoutSourceSet: String = withoutSourceSet(module.getName)

    /**
     * The sbt project's own name, without the build prefix of [[nameWithoutSourceSet]], e.g. `C` for `root.C.main`.
     * `None` for a module with no sbt display name. Unlike the module name it need not be unique: projects of
     * different builds may share it.
     */
    def displayNameWithoutSourceSet: Option[String] =
      Option(DisplayModuleName.getInstance(module)).flatMap(display => Option(display.name))
        .filter(_.nonEmpty)
        .map(withoutSourceSet)

    private def withoutSourceSet(name: String): String =
      if (isSbtSourceSetModule) withoutSourceSetSuffix(name)
      else name

    private def is(sourceSetType: SourceSetType): Boolean =
      isSbtSourceSetModule && {
        // NOTE!!
        // In some edge cases, the source set module name may also end with a number e.g., .main~<number>,
        // so we should handle this scenario as well
        val pattern = s"^.*\\.$sourceSetType(~\\d+)?$$".r
        pattern.matches(module.getName)
      }
  }

  /**
   * `name` without the suffix sbt source set modules are named with, e.g. `root.C` for `root.C.main` or
   * `root.C.test~1`; `name` itself when it has none.
   */
  def withoutSourceSetSuffix(name: String): String = SourceSetSuffix.replaceFirstIn(name, "")

  private val SourceSetSuffix = s"\\.(${SourceSetType.values.mkString("|")})(~\\d+)?$$".r
}
