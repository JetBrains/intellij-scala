package org.jetbrains.jps.incremental.scala
package local

import java.io._

import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.messages.{BuildMessage, CompilerMessage}

import scala.collection.mutable

/**
  * @author Nikolay.Tropin
  */
class PackageObjectsData extends Serializable {

  private val baseSourceToPackageObjects = mutable.HashMap[File, Set[File]]()
  private val packageObjectToBaseSources = mutable.HashMap[File, Set[File]]()

  def add(baseSource: File, packageObject: File): Unit = synchronized {
    baseSourceToPackageObjects.update(baseSource, baseSourceToPackageObjects.getOrElse(baseSource, Set.empty) + packageObject)
    packageObjectToBaseSources.update(packageObject, packageObjectToBaseSources.getOrElse(packageObject, Set.empty) + baseSource)
  }

  def invalidatedPackageObjects(sources: collection.Seq[File]): Set[File] = synchronized {
    sources.toSet.flatMap((f: File) => baseSourceToPackageObjects.getOrElse(f, Set.empty)) -- sources
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
    def warning(message: String): Unit = {
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