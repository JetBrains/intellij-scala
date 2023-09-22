package org.jetbrains.jps.incremental.scala
package local.zinc

import java.io.File
import java.util.Optional
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.incremental.scala.local.ClassFileUtils
import sbt.internal.inc.PlainVirtualFileConverter
import xsbti.VirtualFile
import xsbti.compile.ExternalHooks.Lookup
import xsbti.compile.{ClassFileManager, ExternalHooks}

case class IntelljExternalHooks(lookup: IntellijExternalLookup,
                                classFileManager: ClassFileManager)
  extends ExternalHooks {
  override def getExternalLookup: Optional[Lookup] = Optional.of(lookup)

  override def withExternalClassFileManager(classFileManager: ClassFileManager): ExternalHooks = this

  override def withExternalLookup(lookup: Lookup): ExternalHooks = this

  override def getExternalClassFileManager: Optional[ClassFileManager] = Optional.of(classFileManager)
}

class IntellijClassfileManager extends ClassFileManager {
  private var _deleted: Seq[Array[File]] = Nil
  private var _generated: Seq[Array[File]] = Nil

  override def delete(classes: Array[VirtualFile]): Unit = {
    val tastyFiles = classes.flatMap { virtualFile =>
      ClassFileUtils.correspondingTastyFile(PlainVirtualFileConverter.converter.toPath(virtualFile).toFile)
    }
    tastyFiles.foreach(FileUtil.delete)
    _deleted :+= classes.map { virtualFile =>
      PlainVirtualFileConverter.converter.toPath(virtualFile).toFile
    }
  }

  final override def delete(classes: Array[File]): Unit =
    delete(classes.map { file =>
      PlainVirtualFileConverter.converter.toVirtualFile(file.toPath)
    })

  override def complete(success: Boolean): Unit = {}

  override def generated(classes: Array[VirtualFile]): Unit = _generated :+= classes.map { virtualFile =>
    PlainVirtualFileConverter.converter.toPath(virtualFile).toFile
  }

  final override def generated(classes: Array[File]): Unit =
    generated(classes.map { file =>
      PlainVirtualFileConverter.converter.toVirtualFile(file.toPath)
    })

  def deletedDuringCompilation(): Seq[Array[File]] = _deleted

  def generatedDuringCompilation(): Seq[Array[File]] = _generated
}
