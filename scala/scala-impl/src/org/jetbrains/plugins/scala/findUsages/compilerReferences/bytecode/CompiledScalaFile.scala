package org.jetbrains.plugins.scala.findUsages.compilerReferences
package bytecode

import java.io.File
import java.{util => ju}

import org.jetbrains.jps.backwardRefs.CompilerRef
import org.jetbrains.plugins.scala.findUsages.compilerReferences.indices.{ScFunExprCompilerRef, ScalaCompilerReferenceWriter}

private[findUsages] final case class CompiledScalaFile private (
  file:              File,
  backwardHierarchy: ju.Map[CompilerRef, collection.Seq[CompilerRef]],
  refs:              ju.Map[CompilerRef, collection.Seq[Int]]
)

private[compilerReferences] object CompiledScalaFile {
  def apply(
    source:  File,
    classes: Set[ParsedClass],
    writer:  ScalaCompilerReferenceWriter
  ): CompiledScalaFile = {
    val backwardHierarchy = new ju.HashMap[CompilerRef, collection.Seq[CompilerRef]]()
    val refs              = new ju.HashMap[CompilerRef, collection.Seq[Int]]()
    val refProvider       = new BytecodeReferenceCompilerRefProvider(writer)

    classes.foreach { parsed =>
      val className = writer.enumerateName(parsed.classInfo.fqn)
      val classRef = parsed match {
        case cl: RegularClass =>
          if (cl.classInfo.isAnonymous) new CompilerRef.JavaCompilerAnonymousClassRef(className)
          else                          new CompilerRef.JavaCompilerClassRef(className)
        case anon: FunExprClass => ScFunExprCompilerRef(anon.line)
      }

      val superClasses: Set[CompilerRef] = parsed.classInfo.superClasses
        .map(className => new CompilerRef.JavaCompilerClassRef(writer.enumerateName(className)))

      superClasses.foreach(backwardHierarchy.merge(_, Seq(classRef), _ ++ _))
      parsed.funExprs.foreach { sam =>
        val ref     = new ScFunExprCompilerRef(sam.line)
        val ifaceId = writer.enumerateName(sam.interface)
        val iface   = new CompilerRef.JavaCompilerClassRef(ifaceId)
        backwardHierarchy.merge(iface, Seq(ref), _ ++ _)
      }
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
          val lines: Seq[Int] = rs.map(_.line).toSeq
          refs.put(compilerRef, lines)
      }

    new CompiledScalaFile(source, backwardHierarchy, refs)
  }
}
