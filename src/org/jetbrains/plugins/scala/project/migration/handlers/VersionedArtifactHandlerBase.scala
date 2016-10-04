package org.jetbrains.plugins.scala.project.migration.handlers

import java.io.File

import com.intellij.openapi.externalSystem.model.project.{LibraryData, LibraryPathType}
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.project.template.{Artifact, Component}

import scala.collection.JavaConverters._

/**
  * User: Dmitry.Naydanov
  * Date: 06.09.16.
  */
abstract class VersionedArtifactHandlerBase(val myArtifact: Artifact, versionFrom: Seq[Version],
                                   versionTo: Seq[Version], continuousVersion: Boolean) extends ScalaLibraryMigrationHandler {
  def this(prefix: String, resource: Option[String], versionFrom: Seq[Version], versionTo: Seq[Version], 
           continuousVersion: Boolean) {
    this(new Artifact(prefix, resource), versionFrom, versionTo, continuousVersion)
  }
  
  
  private val isRangeVersion = versionFrom.size == 1 && versionTo.size == 1 && continuousVersion

  
  protected def extractVersion(files: Seq[File]): Option[Version] =
  Component.discoverIn(Set(getArtifact), files).find {
      component => component.artifact.prefix == myArtifact.prefix
    }.flatMap(_.version)
  
  protected def extractVersion(lib: Library): Option[Version] = {
    val ioFiles = lib.getFiles(OrderRootType.CLASSES).map {
      file => new File(file.getPath.replace('/', File.separatorChar).replace(".jar!", ".jar")) //todo better way?
    }

    extractVersion(ioFiles)
  }
  
  protected def extractVersion(lib: LibraryData): Option[Version] = {
    val ioFiles = lib.getPaths(LibraryPathType.BINARY).asScala.map {
      path => new File(path)
    }
    
    extractVersion(ioFiles.toSeq)
  }
  
  protected def isVersionMoreSpecific(lessSpecific: Version, moreSpecific: Version, strict: Boolean = false): Boolean = {
    if (lessSpecific == moreSpecific) return !strict
    lessSpecific.digitsIterator.zip(moreSpecific.digitsIterator).foreach {
      case (a, b) => if (a != b) return false
    }
    true
  } 

  override def acceptsFrom(from: Library): Boolean = extractVersion(from).exists {
    version => versionFrom.contains(version) || (
      isRangeVersion && (versionFrom.head <= version) && (versionTo.head > version))
  }

  override def acceptsTo(to: LibraryData): Boolean = extractVersion(to).exists {
    version => versionTo.contains(version) || (
      isRangeVersion && (versionTo.head >= version) && (versionFrom.head < version))
  }

  override def precede(otherOne: ScalaLibraryMigrationHandler): Boolean = otherOne match {
    case same: VersionedArtifactHandlerBase => 
      same.getArtifact.prefix == myArtifact.prefix && 
        same.getArtifact.resource == myArtifact.resource && 
        isRangeVersion == same.isRangeVersion
    case _ => false
  }

  def getArtifact: Artifact = myArtifact
}
