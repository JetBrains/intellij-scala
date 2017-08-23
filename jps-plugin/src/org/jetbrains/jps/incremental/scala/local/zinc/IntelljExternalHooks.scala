package org.jetbrains.jps.incremental.scala
package local.zinc

import java.io.File
import java.util.Optional

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

class IntellijClassfileManager extends ClassFileManager with ClassfilesChanges {
  private var _deleted: Seq[Array[File]] = Nil
  private var _generated: Seq[Array[File]] = Nil

  override def delete(classes: Array[File]): Unit = _deleted :+= classes

  override def complete(success: Boolean): Unit = {}

  override def generated(classes: Array[File]): Unit = _generated :+= classes

  override def deletedDuringCompilation(): Seq[Array[File]] = _deleted

  override def generatedDuringCompilation(): Seq[Array[File]] = _generated
}