package org.jetbrains.plugins.scala.findUsages.compilerReferences

import org.apache.bcel.Const
import org.apache.bcel.classfile._
import org.jetbrains.jps.backwardRefs.CompilerRef

private class ConstantPoolCompilerRefProvider(
  cp: ConstantPool,
  writer: ScalaCompilerReferenceWriter
) extends CompilerRefProvider[Constant] {
  import ConstantPoolCompilerRefProvider._

  override def toCompilerRef(from: Constant): Option[CompilerRef] = from match {
    case ref: ConstantCP =>
      val internalName = cp.getConstantString(ref.getClassIndex, Const.CONSTANT_Class)
      val ownerName    = fromInternalName(internalName)
      val owner        = writer.enumerateName(ownerName)

      val nameAndType =
        cp.getConstant(ref.getNameAndTypeIndex, Const.CONSTANT_NameAndType)
          .asInstanceOf[ConstantNameAndType]

      val name = nameAndType.getName(cp)
      val id   = writer.enumerateName(name)
      ref match {
        case _: ConstantFieldref           => Option(new CompilerRef.JavaCompilerFieldRef(owner, id))
        case _: ConstantMethodref          => Option(new CompilerRef.JavaCompilerMethodRef(owner, id, 0))
        case _: ConstantInterfaceMethodref => Option(new CompilerRef.JavaCompilerMethodRef(owner, id, 0))
        case _                             => None
      }
    case _ => None
  }

  def unapply(constant: Constant): Option[CompilerRef] = toCompilerRef(constant)
}

private object ConstantPoolCompilerRefProvider {
  def fromInternalName(name: String): String = name.replaceAll("/", ".")
}
