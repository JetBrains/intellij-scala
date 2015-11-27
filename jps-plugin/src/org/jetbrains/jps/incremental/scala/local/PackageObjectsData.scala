package org.jetbrains.jps.incremental.scala
package local

import java.io._

import org.jetbrains.jps.incremental.CompileContext

import scala.collection.immutable.HashSet
import scala.collection.mutable

/**
  * @author Nikolay.Tropin
  */
class PackageObjectsData extends Serializable {

  private val baseSourceToPackageObjects = mutable.HashMap[File, HashSet[File]]()
  private val packageObjectToBaseSources = mutable.HashMap[File, HashSet[File]]()

  def add(baseSource: File, packageObject: File) = {
    baseSourceToPackageObjects.update(baseSource, baseSourceToPackageObjects.getOrElse(baseSource, HashSet.empty) + packageObject)
    packageObjectToBaseSources.update(packageObject, packageObjectToBaseSources.getOrElse(packageObject, HashSet.empty) + baseSource)
  }

  def shouldInvalidateWith(sources: Seq[File]): HashSet[File] = {
    val fromStorage = sources.map(f => baseSourceToPackageObjects.getOrElse(f, HashSet.empty)).reduce(_ ++ _) -- sources
    fromStorage.filter(_.exists())
  }

  def clear() = {
    baseSourceToPackageObjects.clear()
    packageObjectToBaseSources.clear()
  }

  def save(context: CompileContext): Unit = {
    val file = PackageObjectsData.storageFile(context)
    using(new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) { stream =>
      stream.writeObject(this)
    }
  }
}

object PackageObjectsData {

  val packageObjectClassName = "package$"

  private val fileName = "packageObjects.dat"

  private def storageFile(context: CompileContext): File = {
    val storageRoot = context.getProjectDescriptor.dataManager.getDataPaths.getDataStorageRoot
    new File(storageRoot, fileName)
  }

  def load(context: CompileContext): PackageObjectsData = {
    Option(storageFile(context)).filter(_.exists()) match {
      case None => new PackageObjectsData()
      case Some(file) =>
        using(new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)))) { stream =>
          try {
            stream.readObject() match {
              case data: PackageObjectsData => data
              case _ => new PackageObjectsData()
            }
          } catch {
            case ioe: IOException => new PackageObjectsData()
          }
        }
    }
  }
}