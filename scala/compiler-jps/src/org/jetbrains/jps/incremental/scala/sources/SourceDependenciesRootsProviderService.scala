package org.jetbrains.jps.incremental.scala.sources

import com.intellij.openapi.util.io.FileFilters

import java.util
import java.util.Collections
import org.jetbrains.jps.builders.java.{JavaModuleBuildTargetType, JavaSourceRootDescriptor}
import org.jetbrains.jps.builders.storage.BuildDataPaths
import org.jetbrains.jps.builders.{AdditionalRootsProviderService, BuildTarget}
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.scala.SourceDependenciesProviderService
import org.jetbrains.jps.model.java.{JavaSourceRootProperties, JavaSourceRootType}
import org.jetbrains.jps.model.module.JpsModuleSourceRoot

import scala.jdk.CollectionConverters._

class SourceDependenciesRootsProviderService
  extends AdditionalRootsProviderService[JavaSourceRootDescriptor](JavaModuleBuildTargetType.ALL_TYPES) {

  override def getAdditionalRoots(target: BuildTarget[JavaSourceRootDescriptor],
                                  dataPaths: BuildDataPaths): util.List[JavaSourceRootDescriptor] = {

    target match {
      case target: ModuleBuildTarget =>
        val sourceDepModules = SourceDependenciesProviderService.getSourceDependenciesFor(target)
        val sourceRoots = sourceDepModules.flatMap(_.getSourceRoots(rootType(target)).asScala)
        sourceRoots.map(descriptor(_, target)).asJava
      case _ =>
        super.getAdditionalRoots(target, dataPaths)
    }
  }

  private def descriptor(sourceRoot: JpsModuleSourceRoot, target: ModuleBuildTarget): JavaSourceRootDescriptor = {
    val packagePrefix = sourceRoot.getProperties match {
      case j: JavaSourceRootProperties => j.getPackagePrefix
      case _ => ""
    }
    new JavaSourceRootDescriptor(sourceRoot.getFile, target, false, false, packagePrefix, Collections.emptySet(), FileFilters.EVERYTHING)
  }

  private def rootType(moduleBuildTarget: ModuleBuildTarget): JavaSourceRootType =
    if (moduleBuildTarget.isTests) JavaSourceRootType.TEST_SOURCE
    else JavaSourceRootType.SOURCE

}