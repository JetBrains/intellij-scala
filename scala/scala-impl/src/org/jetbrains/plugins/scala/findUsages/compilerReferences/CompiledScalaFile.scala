package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io.File
import java.{util => ju}

import org.jetbrains.jps.backwardRefs.CompilerRef

private[findUsages] final case class CompiledScalaFile private (
  file:              File,
  backwardHierarchy: ju.Map[CompilerRef, CompilerRef],
  refs:              ju.Map[CompilerRef, Set[Int]]
)

private object CompiledScalaFile {
  def apply(
    source:  File,
    classes: Set[ParsedClassfile],
    writer:  ScalaCompilerReferenceWriter
  ): CompiledScalaFile = {
    val backwardHierarchy = new ju.HashMap[CompilerRef, CompilerRef]()
    val refs = new ju.HashMap[CompilerRef, Set[Int]]()
    val refProvider = new BytecodeReferenceCompilerRefProvider(writer)

    classes.foreach { parsed =>
      val className = writer.enumerateName(parsed.classInfo.fqn)
      val classRef = new CompilerRef.JavaCompilerClassRef(className)

      val superClasses: Set[CompilerRef] = parsed.classInfo.superClasses
        .map(className => new CompilerRef.JavaCompilerClassRef(writer.enumerateName(className)))

      superClasses.foreach(backwardHierarchy.put(_, classRef))
    }

    classes
      .flatMap(_.refs)
      .groupBy {
        case mref: MethodReference => (mref.fqn, 0)
        case fref: FieldReference  => (fref.fqn, 1)
      }
      .foreach {
        case (_, rs) =>
          val compilerRef     = refProvider.toCompilerRef(rs.head)
          val lines: Set[Int] = rs.map(_.line)(collection.breakOut)
          refs.put(compilerRef, lines)
      }

    new CompiledScalaFile(source, backwardHierarchy, refs)
  }
}
