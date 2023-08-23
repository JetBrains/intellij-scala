package org.jetbrains.plugins.scala.project.sdkdetect.repository

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.scala.project.sdkdetect.repository.CompilerClasspathResolveFailure.NotSupportedForScalaVersion
import org.jetbrains.plugins.scala.project.template.ScalaSdkDescriptor

trait ScalaSdkDetectorCompilerClasspathResolveOps {
  self: ScalaSdkDetectorBase =>

  /**
   * In Scala2 the approach to detect Scala SDK is quite straightforward, we just search for a fixed set of compiler jars:
   *  - `scala-compiler-2.X.Y.jar`
   *  - `scala-library-2.X.Y.jar`
   *  - `scala-reflect-2.X.Y.jar`.
   * All those jars have same Scala2 version (whether directly in name or in *.properties file)<br>
   * This makes process of building of "SDK candidates" quite easy.<br>
   * You just search the jars by name prefix in some folder (e.g. in "org.scala-lang" for Ivy OR "org" / "scala-lang" for Maven)
   * and group them by version.
   *
   * In Scala3 things get different. Scala 3 compiler classpath has dependencies on some non-scala3 libraries which have their own versions.
   * Each minor/major Scala 3 version can have dependencies with different versions.
   * To detect the correct Scala 3 SDK we need to dynamically resolve those versions when building "SDK candidates" and check if they exist locally.
   * This logic is different for different detectors (Ivy, Maven, Brew...)
   *
   * @return new descriptor with extra jars attached to the compiler classpath and/or library/sources jars
   */
  final def resolveExtraRequiredJars(descriptor: ScalaSdkDescriptor)
                                    (implicit indicator: ProgressIndicator): Either[Seq[CompilerClasspathResolveFailure], ScalaSdkDescriptor] = {
    val descriptorUpdated = if (descriptor.isScala3)
      resolveExtraRequiredJarsScala3(descriptor)
    else
      resolveExtraRequiredJarsScala2(descriptor)
    descriptorUpdated
  }

  protected def resolveExtraRequiredJarsScala2(descriptor: ScalaSdkDescriptor)
                                              (implicit indicator: ProgressIndicator): Either[Seq[CompilerClasspathResolveFailure], ScalaSdkDescriptor] =
    Right(descriptor)

  protected def resolveExtraRequiredJarsScala3(descriptor: ScalaSdkDescriptor)
                                              (implicit indicator: ProgressIndicator): Either[Seq[CompilerClasspathResolveFailure], ScalaSdkDescriptor] =
    Left(Seq(NotSupportedForScalaVersion(descriptor.version.getOrElse("<unknown>"), this)))
}