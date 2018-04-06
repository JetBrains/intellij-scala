package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io._
import java.{util => ju}

import scala.collection.JavaConverters._
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.org.objectweb.asm._

import scala.collection.mutable

private object ClassfileParser {
  def parse(is: InputStream): ParsedClassfile = {
    val reader  = new ClassReader(is)
    val visitor = new ParsingVisitor
    reader.accept(visitor, ClassReader.SKIP_FRAMES)
    visitor.result
  }

  def parse(bytes: Array[Byte]): ParsedClassfile = parse(new ByteArrayInputStream(bytes))
  def parse(file: File): ParsedClassfile         = parse(new FileInputStream(file))
  def parse(vfile: VirtualFile): ParsedClassfile = parse(vfile.getInputStream)

  def fqnFromInternalName(internal: String): String = internal.replaceAll("/", ".")

  private[this] trait ReferenceCollector {
    def addRef(ref: MemberReference): Unit
  }

  private[this] class ParsingVisitor extends ClassVisitor(Opcodes.API_VERSION) {
    private[this] var className: String                                = _
    private[this] val superNames: mutable.Builder[String, Set[String]] = Set.newBuilder[String]
    private[this] val innerRefs: ju.List[MemberReference]              = new ju.ArrayList[MemberReference]()

    override def visit(
      version: Int,
      access: Int,
      name: String,
      signature: String,
      superName: String,
      interfaces: Array[String]
    ): Unit = {
      className = fqnFromInternalName(name)
      superNames += fqnFromInternalName(superName)

      if (interfaces != null) {
        interfaces.map(fqnFromInternalName).foreach(superNames += _)
      }
    }

    override def visitMethod(
      access: Int,
      name: String,
      desc: String,
      signature: String,
      exceptions: Array[String]
    ): MethodVisitor = new MethodVisitor(Opcodes.API_VERSION) with ReferenceInMethodCollector {
      override def addRef(ref: MemberReference): Unit = innerRefs.add(ref)
    }

    def result: ParsedClassfile = {
      val classInfo = ClassInfo(className, superNames.result())
      ParsedClassfile(classInfo, innerRefs.asScala)
    }
  }

  private[this] trait ReferenceInMethodCollector extends ReferenceCollector {
    self: MethodVisitor =>

    private[this] var currentLineNumber = -1

    private[this] def handleRef(
      owner: String,
      name: String
    )(builder: (String, String, Int) => MemberReference): Unit = {
      val ownerFqn = fqnFromInternalName(owner)
      val ref      = builder(ownerFqn, name, currentLineNumber)
      addRef(ref)
    }

    override def visitLineNumber(line: Int, start: Label): Unit = currentLineNumber = line

    override def visitMethodInsn(opcode: Int, owner: String, name: String, desc: String, itf: Boolean): Unit = {
      val argsCount = Type.getArgumentTypes(desc).length
      handleRef(owner, name)(MethodReference(_, _, _, argsCount))
    }

    override def visitFieldInsn(opcode: Int, owner: String, name: String, desc: String): Unit =
      handleRef(owner, name)(FieldReference)
  }
}
