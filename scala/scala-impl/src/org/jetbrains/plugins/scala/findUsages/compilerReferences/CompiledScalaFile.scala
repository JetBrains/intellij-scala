package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io.File
import java.{util => ju}
import java.util.Collections

import scala.collection.JavaConverters._

import org.jetbrains.jps.backwardRefs.CompilerRef
import org.jetbrains.jps.backwardRefs.index.CompiledFileData

private[findUsages] final case class CompiledScalaFile private (
  file: File,
  backwardHierarchy: ju.Map[CompilerRef, ju.Collection[CompilerRef]],
  refs: ju.Map[CompilerRef, Set[Int]]
) extends CompiledFileData(
      backwardHierarchy,
      Collections.emptyMap(),
      Collections.emptyMap(),
      Collections.emptyMap(),
      Collections.emptyMap()
    )

private object CompiledScalaFile {
  def apply(
    source: File,
    classes: Set[ParsedClassfile],
    writer: ScalaCompilerReferenceWriter
  ): CompiledScalaFile = {
    val backwardHierarchy = new ju.HashMap[CompilerRef, ju.Collection[CompilerRef]]()
    val refs              = new ju.HashMap[CompilerRef, Set[Int]]()
    val refProvider       = new BytecodeReferenceCompilerRefProvider(writer)

    classes.foreach { parsed =>
      val className = writer.enumerateName(parsed.classInfo.fqn)
      val classRef  = new CompilerRef.JavaCompilerClassRef(className)

      val superClasses: Set[CompilerRef] = parsed.classInfo.superClasses
        .map(className => new CompilerRef.JavaCompilerClassRef(writer.enumerateName(className)))

      backwardHierarchy.put(classRef, superClasses.asJava)

      parsed.refs.groupBy(_.fullName).map {
        case (_, rs) =>
          val compilerRef     = refProvider.toCompilerRef(rs.head)
          val lines: Set[Int] = rs.map(_.line)(collection.breakOut)
          refs.put(compilerRef, lines)
      }
    }

    new CompiledScalaFile(source, backwardHierarchy, refs)
  }
}
