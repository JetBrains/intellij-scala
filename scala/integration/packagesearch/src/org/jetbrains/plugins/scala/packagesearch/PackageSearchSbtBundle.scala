package org.jetbrains.plugins.scala.packagesearch

import com.intellij.DynamicBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.BuildSystemType
import org.jetbrains.annotations.{Nls, PropertyKey}
import org.jetbrains.plugins.scala.NlsString

import scala.annotation.varargs

object PackageSearchSbtBundle {
  private final val BUNDLE = "messages.PackageSearchSbtBundle"
  private object INSTANCE extends DynamicBundle(BUNDLE)

  val buildSystemType = new BuildSystemType(
    "SBT",
    "scala",
    "sbt-scala"
  )

  //noinspection ReferencePassedToNls
  @Nls
  @varargs
  def message(@PropertyKey(resourceBundle = BUNDLE) key: String, params: Any*): String =
    INSTANCE.getMessage(key, params.map(_.toString): _*)

  //noinspection ReferencePassedToNls
  @Nls
  @varargs
  def nls(@PropertyKey(resourceBundle = BUNDLE) key: String, params: Any*): NlsString =
    NlsString(INSTANCE.getMessage(key, params.map(_.toString): _*))
}
