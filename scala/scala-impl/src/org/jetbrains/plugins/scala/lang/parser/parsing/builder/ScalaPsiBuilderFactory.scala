package org.jetbrains.plugins.scala.lang.parser.parsing.builder

import com.intellij.lang.PsiBuilder
import com.intellij.psi.impl.source.resolve.FileContextUtil.CONTAINING_FILE_KEY
import org.jetbrains.plugins.scala.project.ProjectPsiFileExt.enableFeaturesCheckInTests
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.{ScalaVersion, isUnitTestMode}

object ScalaPsiBuilderFactory {
  /**
   * `features` and `isStrictMode` are computed lazily: computing them queries (and caches) the module of the
   * containing file, which must not happen before it's actually needed (e.g. synthetic files get their context later)
   */
  final class Settings private[ScalaPsiBuilderFactory] (
    delegate: PsiBuilder,
    isScala3: Boolean,
    presetFeatures: Option[ScalaFeatures],
  ) {
    private lazy val containingFile = Option(delegate.getUserData(CONTAINING_FILE_KEY))

    lazy val features: ScalaFeatures = presetFeatures.getOrElse {
      def featuresByVersion: ScalaFeatures = {
        val version = if (isScala3) ScalaVersion.Latest.Scala_3_5 else ScalaVersion.Latest.Scala_2_13
        ScalaFeatures.onlyByVersion(version)
      }

      containingFile
        .flatMap { file =>
          val fileModule = file.module
          val moduleFeatures = fileModule.map(_.features)
          moduleFeatures.orElse(ScalaFeaturePusher.getFeatures(file))
        }
        .getOrElse(featuresByVersion)
    }

    lazy val isStrictMode: Boolean = containingFile.exists(_.isCompilerStrictMode)

    val allowTrailingCommaByDefault: Boolean = isUnitTestMode && !enableFeaturesCheckInTests
  }

  def settings(
    delegate: PsiBuilder,
    isScala3: Boolean,
    presetFeatures: Option[ScalaFeatures] = None,
  ): Settings =
    new Settings(delegate, isScala3, presetFeatures)

  def create(
    delegate: PsiBuilder,
    isScala3: Boolean,
    presetFeatures: Option[ScalaFeatures] = None,
  ): ScalaPsiBuilderImpl = {
    val settings = this.settings(delegate, isScala3, presetFeatures)

    new ScalaPsiBuilderImpl(
      delegate,
      isScala3,
      settings.features,
      settings.isStrictMode,
      settings.allowTrailingCommaByDefault,
    )
  }
}
