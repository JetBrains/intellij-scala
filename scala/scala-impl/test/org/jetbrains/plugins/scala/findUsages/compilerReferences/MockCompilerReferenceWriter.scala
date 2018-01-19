package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.util

import org.jetbrains.jps.backwardRefs.CompilerRef

import scala.collection.{mutable => m}

private class MockCompilerReferenceWriter extends ScalaCompilerReferenceWriter(null) {
  override def writeData(id: Int, d: ClassfileData): Unit                = ()
  override def setRebuildCause(e: Throwable): Unit                       = ()
  override def processDeletedFiles(files: util.Collection[String]): Unit = ()
  override def close(): Unit                                             = ()

  private val refs = m.ArrayBuffer.empty[String]

  override def enumerateName(name: String): Int = {
    refs += name
    refs.size - 1
  }

  def getRefName(ref: CompilerRef.NamedCompilerRef): String = refs(ref.getName)
}
