package org.jetbrains.plugins.scala.compiler.references.bytecode

import org.jetbrains.jps.backwardRefs.CompilerRef
import org.jetbrains.jps.backwardRefs.CompilerRef.{JavaCompilerFieldRef, JavaCompilerMethodRef}
import org.jetbrains.plugins.scala.compiler.references.indices.ScalaCompilerReferenceWriter

private class BytecodeReferenceCompilerRefProvider(writer: ScalaCompilerReferenceWriter)
    extends CompilerRefProvider[MemberReference] {

  override def toCompilerRef(ref: MemberReference): CompilerRef = {
    val ownerId = writer.enumerateName(ref.owner)
    val nameId  = writer.enumerateName(ref.name)

    ref match {
      case mref: MethodReference => new JavaCompilerMethodRef(ownerId, nameId, mref.args)
      case _: FieldReference     => new JavaCompilerFieldRef(ownerId, nameId)
    }
  }
}
