package org.jetbrains.plugins.scala.project.bsp

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.magicmetamodel.impl.workspacemodel.impl.updaters.transformers.{ScalaUtil => BspScalaUtil}
import org.jetbrains.plugins.bsp.extension.points.ScalaSdkGetterExtension
import org.jetbrains.plugins.bsp.server.tasks.ScalaSdk
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.plugins.scala.project.external.ScalaSdkUtils

import java.net.URI
import java.nio.file.Paths

class ScalaSdkGetter extends ScalaSdkGetterExtension {

  override def addScalaSdk(scalaSdk: ScalaSdk, ideModifiableModelsProvider: IdeModifiableModelsProvider): Unit = {
    if (ScalaLanguageLevel.findByVersion(scalaSdk.getScalaVersion).isEmpty) return

    val scalaSdkName = BspScalaUtil.INSTANCE.scalaVersionToScalaSdkName(scalaSdk.getScalaVersion)
    val projectLibrariesModel = ideModifiableModelsProvider.getModifiableProjectLibrariesModel
    val existingScalaLibrary = projectLibrariesModel.getLibraries.find(_.getName == scalaSdkName)
    val sdkJars = scalaSdk.getSdkJars.toArray().map(uri => Paths.get(URI.create(uri.toString)).toFile)
    val scalaLibrary = existingScalaLibrary.getOrElse(projectLibrariesModel.createLibrary(scalaSdkName))

    sdkJars
      .filter(jar =>
        projectLibrariesModel.getLibraries.forall(_.getName != jar.getName) &&
          VfsUtil.findFileByIoFile(jar, true) != null
      ).foreach { jar =>
        val jarLib = projectLibrariesModel.createLibrary(jar.getName)
        val model = jarLib.getModifiableModel
        model.addRoot(VfsUtil.getUrlForLibraryRoot(jar), OrderRootType.CLASSES)
        model.commit()
      }

    ScalaSdkUtils.ensureScalaLibraryIsConvertedToScalaSdk(
      modelsProvider = ideModifiableModelsProvider,
      library = scalaLibrary,
      maybeVersion = Some(scalaSdk.getScalaVersion),
      compilerClasspath = sdkJars.toSeq,
      scaladocExtraClasspath = Nil,
      compilerBridgeBinaryJar = None
    )
  }
}
