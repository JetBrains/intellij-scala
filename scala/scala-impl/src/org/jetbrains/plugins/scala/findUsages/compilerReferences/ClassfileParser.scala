package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io._

import com.intellij.openapi.vfs.VirtualFile
import org.apache.bcel.Const
import org.apache.bcel.classfile.{Attribute, ConstantPool, SourceFile}

import scala.annotation.tailrec

private object ClassfileParser {
  private def skipMagicAndVersion(is: DataInputStream): Unit =
    is.skipBytes(4 + 2 + 2) // magic, minor & major versions

  private def skipClassBody(is: DataInputStream): Unit = {
    //fields
    (1 to is.readUnsignedShort()).foreach { field =>
      is.skipBytes(2 + 2 + 2) // access, name, desc
      (1 to is.readUnsignedShort()).foreach { attr =>
        is.skipBytes(2)
        is.skipBytes(is.readInt()) // name + info[length]
      }
    }

    // methods
    (1 to is.readUnsignedShort()).foreach { method =>
      is.skipBytes(2 + 2 + 2) // access, name, desc
      (1 to is.readUnsignedShort()).foreach { attr =>
        is.skipBytes(2)
        is.skipBytes(is.readInt()) // name + info[length]
      }
    }
  }

  private def readClassInfo(is: DataInputStream, cp: ConstantPool): ClassInfo = {
    is.skip(2) // access
    val name           = fqnFromInternalName(cp.getConstantString(is.readUnsignedShort(), Const.CONSTANT_Class))
    val superClass     = fqnFromInternalName(cp.getConstantString(is.readUnsignedShort(), Const.CONSTANT_Class))
    val interfaceCount = is.readUnsignedShort()
    val interfaces     = (1 to interfaceCount).map(_ => fqnFromInternalName(cp.getConstantString(is.readUnsignedShort(), Const.CONSTANT_Class)))
    ClassInfo(name, superClass +: interfaces)
  }

  private def readSourceAttribute(is: DataInputStream, cp: ConstantPool): Option[String] = {
    val count = is.readUnsignedShort()

    @tailrec
    def process(processed: Int = 0): Option[String] =
      if (processed < count) {
        Attribute.readAttribute(is, cp) match {
          case sf: SourceFile => Option(sf.getSourceFileName)
          case _              => process(processed + 1)
        }
      } else None

    process()
  }

  def parse(is: DataInputStream): ParsedClassfile = {
    skipMagicAndVersion(is)
    val cp        = new ConstantPool(is)
    val classInfo = readClassInfo(is, cp)
//    skipClassBody(is)
//    val source = readSourceAttribute(is, cp)
    ParsedClassfile(classInfo, cp)
  }

  def parse(bytes: Array[Byte]): ParsedClassfile = parse(new DataInputStream(new ByteArrayInputStream(bytes)))
  def parse(is: InputStream): ParsedClassfile    = parse(new DataInputStream(is))
  def parse(file: File): ParsedClassfile         = parse(new DataInputStream(new FileInputStream(file)))
  def parse(vfile: VirtualFile): ParsedClassfile = parse(new DataInputStream(vfile.getInputStream))
  
  def fqnFromInternalName(name: String): String = name.replaceAll("/", ".")
}
