package org.jetbrains.jps.incremental.scala
package local

import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.messages.{BuildMessage, CompilerMessage}
import org.jetbrains.jps.incremental.scala.remote.SerializablePath

import java.io.{BufferedInputStream, BufferedOutputStream, ObjectInputStream, ObjectOutputStream}
import java.nio.file.{Files, Path}
import scala.collection.mutable
import scala.util.Using

class PackageObjectsData extends Serializable {

  private val baseSourceToPackageObjects = mutable.HashMap.empty[SerializablePath, Set[SerializablePath]]
  private val packageObjectToBaseSources = mutable.HashMap.empty[SerializablePath, Set[SerializablePath]]

  def add(baseSource: Path, packageObject: Path): Unit = synchronized {
    val baseSourceSerializable = SerializablePath(baseSource)
    val packageObjectSerializable = SerializablePath(packageObject)
    baseSourceToPackageObjects.update(baseSourceSerializable, baseSourceToPackageObjects.getOrElse(baseSourceSerializable, Set.empty) + packageObjectSerializable)
    packageObjectToBaseSources.update(packageObjectSerializable, packageObjectToBaseSources.getOrElse(packageObjectSerializable, Set.empty) + baseSourceSerializable)
  }

  def invalidatedPackageObjects(sources: Seq[Path]): Set[Path] = synchronized {
    sources.toSet[Path].flatMap(f => baseSourceToPackageObjects.getOrElse(SerializablePath(f), Set.empty)).map(_.toPath) -- sources
  }

  def clear(): Unit = synchronized {
    baseSourceToPackageObjects.clear()
    packageObjectToBaseSources.clear()
  }

  def save(context: CompileContext): Unit = {
    val file = PackageObjectsData.storageFile(context)
    PackageObjectsData.synchronized {
      Using.resource(new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) { stream =>
        stream.writeObject(this)
        stream.flush()
      }
    }
  }
}

object PackageObjectsData {

  private val fileName = "packageObjects.dat"

  private val instances = mutable.HashMap.empty[Path, PackageObjectsData]

  private def storageFile(context: CompileContext): Path = {
    val storageRoot = context.getProjectDescriptor.dataManager.getDataPaths.getDataStorageDir
    storageRoot.resolve(fileName)
  }

  def getFor(context: CompileContext): PackageObjectsData = {
    def warning(message: String): Unit = {
      context.processMessage(new CompilerMessage("scala", BuildMessage.Kind.WARNING, message))
    }

    def tryToReadData(file: Path): PackageObjectsData =
      synchronized {
        Using(new ObjectInputStream(new BufferedInputStream(Files.newInputStream(file)))) { stream =>
          stream.readObject().asInstanceOf[PackageObjectsData]
        }.recover {
          case e: Exception =>
            warning(s"Could not read data about package objects dependencies: \n${e.getMessage}")
            Files.deleteIfExists(file)
            new PackageObjectsData()
        }.get
      }

    def getOrLoadInstance(file: Path): PackageObjectsData =
      instances.getOrElseUpdate(file, tryToReadData(file))

    Option(storageFile(context))
      .filter(Files.exists(_))
      .fold(new PackageObjectsData())(getOrLoadInstance)
  }
}
