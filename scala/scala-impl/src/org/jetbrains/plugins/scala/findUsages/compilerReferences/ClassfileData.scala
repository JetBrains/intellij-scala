package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io.File
import java.util
import java.util.Collections

import scala.collection.Set

import org.jetbrains.jps.backwardRefs.CompilerRef
import org.jetbrains.jps.backwardRefs.index.CompiledFileData

private[findUsages] final case class ClassfileData(
  sourceFile: File,
  aClass: CompilerRef,
  superClasses: Set[CompilerRef.JavaCompilerClassRef],
  refs: Map[CompilerRef, Seq[Int]]
) extends {
  private[this] val backwardsHierarchy: util.Map[CompilerRef, util.Collection[CompilerRef]] = {
    val backHierarchy = new util.HashMap[CompilerRef, util.Collection[CompilerRef]]()
    superClasses.foreach(backHierarchy.put(_, util.Collections.singletonList(aClass)))
    backHierarchy
  }
} with CompiledFileData(
  backwardsHierarchy,
  Collections.emptyMap(),
  Collections.emptyMap(),
  Collections.emptyMap(),
  Collections.emptyMap()
) {
  def backwardsReferences: util.Map[CompilerRef, Seq[Int]] = {
    val usages = new util.HashMap[CompilerRef, Seq[Int]](refs.size)
    refs.foreach(Function.tupled(usages.put))
    usages
  }
}

private object ClassfileData {
  def apply(
    parsed: ParsedClassfile,
    sourceFile: File,
    writer: ScalaCompilerReferenceWriter
  ): ClassfileData = {
    val refProvider = new BytecodeReferenceCompilerRefProvider(writer)
    val className   = writer.enumerateName(parsed.classInfo.fqn)
    val classRef    = new CompilerRef.JavaCompilerClassRef(className)

    val superClasses = parsed.classInfo.superClasses
      .map(className => new CompilerRef.JavaCompilerClassRef(writer.enumerateName(className)))

    val refs = parsed.refs.groupBy(_.fullName).map {
      case (_, rs) =>
        val compilerRef = refProvider.toCompilerRef(rs.head)
        val lines       = rs.map(_.line)
        compilerRef -> lines
    }

    new ClassfileData(sourceFile, classRef, superClasses, refs)
  }
}
