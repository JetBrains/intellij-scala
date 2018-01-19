package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io.File
import java.util
import java.util.Collections

import org.jetbrains.jps.backwardRefs.CompilerRef
import org.jetbrains.jps.backwardRefs.index.CompiledFileData

private[findUsages] final case class ClassfileData(
  sourceFile: File,
  aClass: CompilerRef,
  superClasses: Seq[CompilerRef],
  refs: Seq[CompilerRef]
) extends {
  private[this] val backwardsHierarchy: util.Map[CompilerRef, util.Collection[CompilerRef]] = {
    val backHierarchy = new util.HashMap[CompilerRef, util.Collection[CompilerRef]]()
    superClasses.foreach(backHierarchy.put(_, util.Collections.singletonList(aClass)))
    backHierarchy
  }

  private[this] val backwardsReferences: util.Map[CompilerRef, Integer] = {
    val usages = new util.HashMap[CompilerRef, Integer](refs.size)
    refs.foreach(usages.put(_, 1))
    usages
  }

} with CompiledFileData(
  backwardsHierarchy,
  Collections.emptyMap(),
  backwardsReferences,
  Collections.emptyMap(),
  Collections.emptyMap()
)

private object ClassfileData {
  def apply(
    parsed: ParsedClassfile,
    sourceFile: File,
    writer: ScalaCompilerReferenceWriter
  ): ClassfileData = {
    val refProvider = new ConstantPoolCompilerRefProvider(parsed.cp, writer)
    val className   = writer.enumerateName(parsed.classInfo.fqn)
    val classRef    = new CompilerRef.JavaCompilerClassRef(className)

    val superClasses = parsed.classInfo.superClasses
      .map(className => new CompilerRef.JavaCompilerClassRef(writer.enumerateName(className)))

    val refs = parsed.cp.getConstantPool.flatMap(refProvider.toCompilerRef)

    new ClassfileData(sourceFile, classRef, superClasses, refs)
  }

}
