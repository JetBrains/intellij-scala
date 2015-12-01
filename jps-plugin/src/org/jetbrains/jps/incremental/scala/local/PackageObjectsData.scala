package org.jetbrains.jps.incremental.scala
package local

import java.io._

import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.messages.{BuildMessage, CompilerMessage}

import scala.collection.immutable.HashSet
import scala.collection.mutable

/**
  * @author Nikolay.Tropin
  */
class PackageObjectsData extends Serializable {

  private val baseSourceToPackageObjects = mutable.HashMap[File, HashSet[File]]()
  private val packageObjectToBaseSources = mutable.HashMap[File, HashSet[File]]()

  def add(baseSource: File, packageObject: File): Unit = synchronized {
    baseSourceToPackageObjects.update(baseSource, baseSourceToPackageObjects.getOrElse(baseSource, HashSet.empty) + packageObject)
    packageObjectToBaseSources.update(packageObject, packageObjectToBaseSources.getOrElse(packageObject, HashSet.empty) + baseSource)
  }

  def invalidatedPackageObjects(sources: Seq[File]): Set[File] = synchronized {
    sources.to[HashSet].flatMap(f => baseSourceToPackageObjects.getOrElse(f, HashSet.empty)) -- sources
  }

  def clear(): Unit = synchronized {
    baseSourceToPackageObjects.clear()
    packageObjectToBaseSources.clear()
  }

  def save(context: CompileContext): Unit = {
    val file = PackageObjectsData.storageFile(context)
    PackageObjectsData.synchronized {
      using(new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) { stream =>
        stream.writeObject(this)
        stream.flush()
      }
    }
  }
}

object PackageObjectsData {

  val packageObjectClassName: String = "package$"

  private val fileName = "packageObjects.dat"

  private val instances = mutable.HashMap[File, PackageObjectsData]()

  private def storageFile(context: CompileContext): File = {
    val storageRoot = context.getProjectDescriptor.dataManager.getDataPaths.getDataStorageRoot
    new File(storageRoot, fileName)
  }

  def getFor(context: CompileContext): PackageObjectsData = {
    def warning(message: String) = {
      context.processMessage(new CompilerMessage("scala", BuildMessage.Kind.WARNING, message))
    }

    def tryToReadData(file: File) = {
      synchronized {
        try {
          using(new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)))) { stream =>
            stream.readObject().asInstanceOf[PackageObjectsData]
          }
        } catch {
          case e: Exception =>
            warning(s"Could not read data about package objects dependencies: \n${e.getMessage}")
            file.delete()
            new PackageObjectsData()
        }
      }
    }

    def getOrLoadInstance(file: File) = instances.getOrElseUpdate(file, tryToReadData(file))

    Option(storageFile(context))
      .filter(_.exists)
      .fold(new PackageObjectsData())(getOrLoadInstance)
  }
}