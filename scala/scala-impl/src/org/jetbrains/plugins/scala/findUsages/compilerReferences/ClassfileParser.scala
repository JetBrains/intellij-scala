package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io._
import java.nio.charset.StandardCharsets
import java.{util => ju}

import scala.collection.JavaConverters._
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.BitUtil.isSet
import org.jetbrains.org.objectweb.asm.Opcodes._
import org.jetbrains.org.objectweb.asm._
import org.jetbrains.plugins.scala.decompiler.Decompiler._
import org.jetbrains.plugins.scala.decompiler.scalasig.Parser
import org.jetbrains.plugins.scala.decompiler.scalasig._
import org.jetbrains.plugins.scala.extensions._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.reflect.NameTransformer
import scala.reflect.internal.pickling.ByteCodecs

private object ClassfileParser {
  private[this] implicit class RichScalaSigSymbol(val sym: Symbol) extends AnyVal {
    def encodedName: String = NameTransformer.encode(sym.name)

    @tailrec
    final def enclClass: Symbol = sym match {
      case _: ClassSymbol | _: ObjectSymbol => sym
      case _ =>
        sym.parent match {
          case Some(p) => p.enclClass
          case _       => throw new AssertionError("Failed to resolve parent for enclosing class.")
        }
    }

    def ownerChain: Seq[Symbol] = {
      @tailrec
      def loop(sym: Symbol, acc: List[Symbol] = Nil): List[Symbol] =
        sym.parent match {
          case Some(_: ExternalSymbol) => sym :: acc
          case Some(s)                 => loop(s, sym :: acc)
          case None                    => throw new AssertionError("Empty parent on non External Symbol")
        }

      loop(sym)
    }

    private[this] def className(s: Symbol): String = {
      val classes = ownerChain.collect {
        case cs: ClassSymbol   => cs
        case obj: ObjectSymbol => obj
      }.reverse

      val nested  = classes.map(_.encodedName).mkString("$")
      val postfix = if (classes.last.isModule) "$" else ""
      s"$nested$postfix"
    }

    /**
     * Returns quilified name but with package omitted
     */
    def qualifiedName: String = sym match {
      case _: MethodSymbol =>
        val owner = sym.enclClass
        s"${className(owner)}.${sym.encodedName}"
      case _: ClassSymbol | _: ObjectSymbol => className(sym)
      case _                                => ""
    }
  }

  def parse(classFiles: Set[File]): Set[ParsedClassfile] = {
    val outer = classFiles.minBy(_.getPath.length)

    val scalaSig = using(new FileInputStream(outer)) { in =>
      val reader  = new ClassReader(in)
      val visitor = new ScalaSigVisitor(outer.getPath)
      reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
      visitor.scalaSig
    }

    val synthetics: Set[String] = scalaSig.syntheticSymbols().map(_.qualifiedName)(collection.breakOut)

    classFiles.map(parse(_, synthetics))
  }

  private[this] class ScalaSigVisitor(file: String) extends ClassVisitor(API_VERSION) {
    var scalaSig: ScalaSig = _

    override def visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor = desc match {
      case SCALA_SIG_ANNOTATION | SCALA_LONG_SIG_ANNOTATION =>
        new AnnotationVisitor(API_VERSION) {
          override def visit(name: String, value: scala.Any): Unit =
            if (name == BYTES_VALUE) {
              val bytes = value.asInstanceOf[String].getBytes(StandardCharsets.UTF_8)
              ByteCodecs.decode(bytes)
              scalaSig = Parser.parseScalaSig(bytes, file)
            }
        }
      case _ => null
    }
  }

  def parse(is: InputStream, synthetics: Set[String] = Set.empty): ParsedClassfile = using(is) { in =>
    val reader  = new ClassReader(in)
    val visitor = new ParsingVisitor(synthetics)
    reader.accept(visitor, ClassReader.SKIP_FRAMES)
    visitor.result
  }

  def parse(bytes: Array[Byte], synthetics: Set[String]): ParsedClassfile =
    parse(new ByteArrayInputStream(bytes), synthetics)

  def parse(file: File, synthetics: Set[String]): ParsedClassfile =
    parse(new FileInputStream(file), synthetics)

  def parse(vfile: VirtualFile, synthetics: Set[String]): ParsedClassfile =
    parse(vfile.getInputStream, synthetics)

  def fqnFromInternalName(internal: String): String = internal.replaceAll("/", ".")

  private[this] trait ReferenceCollector {
    def addRef(ref: MemberReference): Unit
  }

  private[this] class ParsingVisitor(synthetics: Set[String]) extends ClassVisitor(API_VERSION) {
    private[this] var className: String                                = _
    private[this] val superNames: mutable.Builder[String, Set[String]] = Set.newBuilder[String]
    private[this] val innerRefs: ju.List[MemberReference]              = new ju.ArrayList[MemberReference]()

    override def visit(
      version:    Int,
      access:     Int,
      name:       String,
      signature:  String,
      superName:  String,
      interfaces: Array[String]
    ): Unit = {
      className = fqnFromInternalName(name)
      superNames += fqnFromInternalName(superName)

      if (interfaces != null) {
        interfaces.map(fqnFromInternalName).foreach(superNames += _)
      }
    }

    private[this] def isStaticForwarder(access: Int, name: String): Boolean =
      isSet(access, ACC_STATIC) && isSet(access, ACC_PUBLIC) &&
        name != "<clinit>" &&
        name != "$init$"

    private[this] def isSynthetic(access: Int, name: String): Boolean = {
      val simpleClassName = className.split("\\.").last
      isSet(access, ACC_SYNTHETIC) || synthetics.contains(s"$simpleClassName.$name")
    }

    override def visitMethod(
      access:     Int,
      name:       String,
      desc:       String,
      signature:  String,
      exceptions: Array[String]
    ): MethodVisitor =
      if (isStaticForwarder(access, name) || isSynthetic(access, name)) null
      else
        new MethodVisitor(API_VERSION) with ReferenceInMethodCollector {
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
      owner:   String,
      name:    String
    )(builder: (String, String, Int) => MemberReference): Unit =
      if (currentLineNumber != -1) {
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
