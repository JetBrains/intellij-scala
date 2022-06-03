package org.jetbrains.plugins.scala.findUsages.compilerReferences
package bytecode

import com.intellij.util.BitUtil.isSet
import org.jetbrains.org.objectweb.asm.Opcodes._
import org.jetbrains.org.objectweb.asm._
import org.jetbrains.plugins.scala.decompiler.Decompiler.{BYTES_VALUE, SCALA_LONG_SIG_ANNOTATION, SCALA_SIG_ANNOTATION}
import org.jetbrains.plugins.scala.decompiler.scalasig._

import java.io._
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import java.{util => ju}
import scala.annotation.tailrec
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.reflect.NameTransformer
import scala.reflect.internal.pickling.ByteCodecs
import scala.util.Using

private[compilerReferences] object ClassfileParser {
  private[this] implicit class RichScalaSigSymbol(private val sym: Symbol) extends AnyVal {
    def encodedName: String = NameTransformer.encode(sym.name)

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

    private[this] def className(): String = {
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
        s"${className()}.${sym.encodedName}"
      case _: ClassSymbol | _: ObjectSymbol => className()
      case _                                => ""
    }
  }

  def parse(classFiles: Set[File]): Set[ParsedClass] = {
    val outer = classFiles.minBy(_.getPath.length)

    val scalaSig = Using.resource(new FileInputStream(outer)) { in =>
      val reader  = new ClassReader(in)
      val visitor = new ScalaSigVisitor(outer.getPath)
      reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
      visitor.scalaSig
    }

    val synthetics: Set[String] = scalaSig.fold(Set.empty[String])(
      _.syntheticSymbols().map(_.qualifiedName).to(Set)
    )

    classFiles.map(parse(_, synthetics))
  }

  private[this] class ScalaSigVisitor(file: String) extends ClassVisitor(Opcodes.ASM6) {
    var scalaSig: Option[ScalaSig] = None

    override def visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor = desc match {
      case SCALA_SIG_ANNOTATION | SCALA_LONG_SIG_ANNOTATION =>
        new AnnotationVisitor(ASM6) {
          override def visit(name: String, value: scala.Any): Unit =
            if (name == BYTES_VALUE) {
              val bytes = value.asInstanceOf[String].getBytes(StandardCharsets.UTF_8)
              ByteCodecs.decode(bytes)
              scalaSig = Option(Parser.parseScalaSig(bytes, file))
            }
        }
      case _ => null
    }
  }

  def parse(is: InputStream, synthetics: Set[String] = Set.empty): ParsedClass = Using.resource(is) { in =>
    val reader  = new ClassReader(in)
    val visitor = new ParsingVisitor(synthetics)
    reader.accept(visitor, ClassReader.SKIP_FRAMES)
    visitor.result
  }

  def parse(file: File, synthetics: Set[String]): ParsedClass =
    parse(new BufferedInputStream(new FileInputStream(file)), synthetics)

  private[this] val pattern = Pattern.compile("/")
  private[this] def fqnFromInternalName(internal: String): String  = pattern.matcher(internal).replaceAll(".")
  private[this] def isFunExprClassname(name:      String): Boolean = name.contains("$anonfun$")

  private[this] trait ReferenceCollector {
    def handleMemberRef(ref:   MemberReference): Unit
    def handleSAMRef(ref:      FunExprInheritor): Unit
    def handleLineNumber(line: Int): Unit
  }

  private[this] class ParsingVisitor(synthetics: Set[String]) extends ClassVisitor(ASM6) {
    private[this] var internalName: String                             = _
    private[this] var className: String                                = _
    private[this] var isAnon: Boolean                                  = false
    private[this] val superNames: mutable.Builder[String, Set[String]] = Set.newBuilder[String]
    private[this] val innerRefs: ju.List[MemberReference]              = new ju.ArrayList[MemberReference]()
    private[this] val funExprs: ju.List[FunExprInheritor]              = new ju.ArrayList[FunExprInheritor]()
    private[this] var earliestSeen: Option[Int]                        = None

    private[this] val methodVisitor = new MethodVisitor(ASM6) with ReferenceInMethodCollector {
      override def handleMemberRef(ref: MemberReference): Unit  = innerRefs.add(ref)
      override def handleSAMRef(ref:    FunExprInheritor): Unit = funExprs.add(ref)

      override def handleLineNumber(line: Int): Unit =
        if (isFunExprClassname(className) && earliestSeen.forall(_ > line))
          earliestSeen = Option(line)
    }

    override def visit(
      version:    Int,
      access:     Int,
      name:       String,
      signature:  String,
      superName:  String,
      interfaces: Array[String]
    ): Unit = {
      internalName = name
      className    = fqnFromInternalName(internalName)
      superNames += fqnFromInternalName(superName)

      if (interfaces != null) {
        interfaces.map(fqnFromInternalName).foreach(superNames += _)
      }
    }

    override def visitInnerClass(name: String, outerName: String, innerName: String, access: Int): Unit =
      if (name == internalName) isAnon = innerName == null

    /**
      * We are only interested in static bytecode entries if it is one of the following:
      * 1. Static initializer in trait/object ($init$, <clinit>)
      * 2. Bodies of anonymous functions ($anonfun$).
      *
      * This avoids indexing various flavours of static forwarders generated by scalac.
      */
    private[this] def isStaticForwarder(access: Int, name: String): Boolean =
      isSet(access, ACC_STATIC) &&
        name != "<clinit>" &&
        name != "$init$" &&
        !name.contains("$anonfun$")

    private[this] def isSynthetic(name: String): Boolean = {
      val simpleClassName = className.split("\\.").lastOption.getOrElse("")
      synthetics.contains(s"$simpleClassName.$name")
    }

    override def visitMethod(
      access:     Int,
      name:       String,
      desc:       String,
      signature:  String,
      exceptions: Array[String]
    ): MethodVisitor =
      if (isStaticForwarder(access, name) || isSynthetic(name)) null
      else                                                              methodVisitor


    def result: ParsedClass = {
      val classInfo = ClassInfo(isAnon, className, superNames.result())
      val refs      = innerRefs.asScala.toSeq

      if (isFunExprClassname(className)) FunExprClass(classInfo, refs, earliestSeen.getOrElse(-1))
      else                               RegularClass(classInfo, refs, funExprs.asScala.toSeq)
    }
  }

  private[this] trait ReferenceInMethodCollector extends ReferenceCollector {
    self: MethodVisitor =>

    private[this] var currentLineNumber = -1

    private[this] def handleMemberRef(
      owner:   String,
      name:    String
    )(builder: (String, String, Int) => MemberReference): Unit = {
      val ownerFqn = fqnFromInternalName(owner)
      val ref      = builder(ownerFqn, name, currentLineNumber)
      handleMemberRef(ref)
    }

    override def visitLineNumber(line: Int, start: Label): Unit = {
      currentLineNumber = line
      handleLineNumber(line)
    }

    override def visitMethodInsn(opcode: Int, owner: String, name: String, desc: String, itf: Boolean): Unit =
      if (currentLineNumber != -1) {
        val argsCount = Type.getArgumentTypes(desc).length
        handleMemberRef(owner, name)(MethodReference(_, _, _, argsCount))
      }

    override def visitInvokeDynamicInsn(name: String, desc: String, bsm: Handle, bsmArgs: AnyRef*): Unit =
      if (currentLineNumber != -1 && bsm.getOwner == "java/lang/invoke/LambdaMetafactory") {
        val samType = Type.getReturnType(desc)
        val ref     = FunExprInheritor(samType.getClassName, currentLineNumber)
        handleSAMRef(ref)
      }

    override def visitFieldInsn(opcode: Int, owner: String, name: String, desc: String): Unit =
      if (currentLineNumber != -1) handleMemberRef(owner, name)(FieldReference)
  }
}
