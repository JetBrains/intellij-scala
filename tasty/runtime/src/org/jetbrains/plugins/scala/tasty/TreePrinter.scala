package org.jetbrains.plugins.scala.tasty

import dotty.tools.tasty.TastyFormat._

import java.lang.Double.longBitsToDouble
import java.lang.Float.intBitsToFloat
import scala.collection.mutable

// TODO refactor
// TODO use StringBuilder
object TreePrinter {

  private def isGivenObject0(typedef: Node): Boolean =
    typedef.hasFlag(OBJECT) && typedef.previousSibling.exists(prev => prev.is(VALDEF) && prev.hasFlag(OBJECT) && prev.hasFlag(GIVEN))

  private def isGivenImplicitClass0(typedef: Node): Boolean = {
    def isImplicitConversion(node: Node) = node.is(DEFDEF) && node.hasFlag(SYNTHETIC) && node.hasFlag(GIVEN) && node.name == typedef.name
    typedef.hasFlag(SYNTHETIC) &&
      (typedef.nextSibling.exists(isImplicitConversion) || typedef.nextSibling.exists(_.nextSibling.exists(_.nextSibling.exists(isImplicitConversion))))
  }

  def textOf(node: Node, definition: Option[Node] = None)(using privateMembers: Boolean = false): String = node match { // TODO settings
    case Node(PACKAGE, _, Seq(Node(TERMREFpkg, Seq(name), _), tail: _*)) => tail match {
      case Seq(node @ Node(PACKAGE, _, _), _: _*) => textOf(node)
      case _ =>
        (if (name == "<empty>") "" else ("package " + name + "\n\n")) + (tail match {
          case Seq(Node(VALDEF, Seq(name1), _: _*), Node(TYPEDEF, Seq(name2), Seq(template, _: _*)), _: _*)
            if (name1.endsWith("$package") && name2.endsWith("$package$")) || (name1 == "package" && name2 == "package$") => // TODO use name type, not contents
            template.children.filter(it => it.is(DEFDEF, VALDEF, TYPEDEF) && it.names != Seq("<init>")).map(textOf(_)).filter(_.nonEmpty).mkString("\n\n")
          case _ =>
            tail.map(textOf(_)).filter(_.nonEmpty).mkString("\n\n")
        })
    }

    case node @ Node(TYPEDEF, Seq(name), Seq(template, _: _*)) if !node.hasFlag(SYNTHETIC) || isGivenImplicitClass0(node) => // TODO why both are synthetic?
      if (!privateMembers && node.hasFlag(PRIVATE)) return ""
      val isEnum = node.hasFlag(ENUM)
      val isObject = node.hasFlag(OBJECT)
      val isGivenObject = isGivenObject0(node)
      val isImplicitClass = node.nextSibling.exists(it => it.is(DEFDEF) && it.hasFlag(SYNTHETIC) && it.hasFlag(IMPLICIT) && it.name == name)
      val isGivenImplicitClass = isGivenImplicitClass0(node)
      val isTypeMember = !template.is(TEMPLATE)
      val isAnonymousGiven = (isGivenObject || isGivenImplicitClass) && name.startsWith("given_") // TODO common method
      val keyword =
        if (isEnum) (if (node.hasFlag(CASE)) "case " else "enum ")
        else if (isObject) (if (isGivenObject) "" else "object ")
        else if (node.hasFlag(TRAIT)) "trait "
        else if (isTypeMember) "type "
        else if (isGivenImplicitClass) "given " else "class "
      val identifier = if (isObject) node.previousSibling.fold(name)(_.name) else name // TODO check type
      val modifiers = modifiersIn(if (isObject) node.previousSibling.getOrElse(node) else node,
        if (isGivenImplicitClass) Set(GIVEN) else (if (isEnum) Set(ABSTRACT, SEALED, CASE, FINAL) else (if (isTypeMember) Set.empty else Set(OPAQUE))), isParameter = false) + (if (isImplicitClass) "implicit " else "")
      textOfAnnotationIn(node) +
      modifiers + keyword + (if (isAnonymousGiven) "" else identifier) + (if (!isTypeMember) textOf(template, Some(node)) else {
        val repr = node.children.headOption.filter(_.is(LAMBDAtpt)).getOrElse(node) // TODO handle LAMBDAtpt in parametersIn?
        val bounds = repr.children.find(_.is(TYPEBOUNDStpt))
        parametersIn(repr, Some(repr)) + (if (bounds.isDefined) boundsIn(bounds.get)
        else " = " + (if (node.hasFlag(OPAQUE)) "???" else simple(repr.children.findLast(_.isTypeTree).map(textOfType(_)).getOrElse("")))) // TODO parameter, { /* compiled code */ }
      })

    case node @ Node(TEMPLATE, _, children) => // TODO method?
      val primaryConstructor = children.find(it => it.is(DEFDEF) && it.names == Seq("<init>"))
      val modifiers = primaryConstructor.map(modifiersIn(_)).map(it => if (it.nonEmpty) " " + it else "").getOrElse("")
      val parameters = primaryConstructor.map(it => parametersIn(it, Some(node), definition, modifiers = modifiers)).getOrElse("")
      val isInEnum = definition.exists(_.hasFlag(ENUM))
      val isInCaseClass = !isInEnum && definition.exists(_.hasFlag(CASE))
      val parents = children.collect {
        case node if node.isTypeTree => node
        case Node(APPLY, _, Seq(Node(SELECTin, _, Seq(Node(NEW, _, Seq(tpe, _: _*)), _: _*)), _: _*)) => tpe
        case Node(APPLY, _, Seq(Node(APPLY, _, Seq(Node(SELECTin, _, Seq(Node(NEW, _, Seq(tpe, _: _*)), _: _*)), _: _*)), _: _*)) => tpe
        case Node(APPLY, _, Seq(Node(TYPEAPPLY, _, Seq(Node(SELECTin, _, Seq(Node(NEW, _, Seq(tpe, _: _*)), _: _*)), _: _*)), _: _*)) => tpe
        case Node(APPLY, _, Seq(Node(APPLY, _, Seq(Node(TYPEAPPLY, _, Seq(Node(SELECTin, _, Seq(Node(NEW, _, Seq(tpe, _: _*)), _: _*)), _: _*)), _: _*)), _: _*)) => tpe
      }.map(textOfType(_, parensRequired = true))
        .filter(s => s.nonEmpty && s != "java.lang.Object" && s != "_root_.scala.runtime.EnumValue" &&
          !(isInCaseClass && s == "_root_.scala.Product" || s == "_root_.scala.Serializable"))
        .map(simple)
      val isInGiven = definition.exists(it => isGivenObject0(it) || isGivenImplicitClass0(it))
      val isInAnonymousGiven = isInGiven && definition.exists(_.name.startsWith("given_")) // TODO common method
      val cases = // TODO check element types
        if (isInEnum) definition.get.nextSibling.get.nextSibling.get.children.head.children.filter(it => it.is(VALDEF) || it.is(TYPEDEF))
        else Seq.empty
      val members = (children.filter(it => it.is(DEFDEF, VALDEF, TYPEDEF) && !primaryConstructor.contains(it)) ++ cases) // TODO type member
        .map(textOf(_, definition)).filter(_.nonEmpty).map(indent)
        .map(s => if (definition.exists(_.hasFlag(ENUM))) s.stripSuffix(" extends " + definition.map(_.name).getOrElse("")) else s) // TODO not text-based (need to know an outer definition)
        .mkString("\n\n")
      parameters +
        (if (isInGiven && (!isInAnonymousGiven || parameters.nonEmpty)) ": " else "") +
        (if (isInGiven) (parents.mkString(" with ") + " with") else (if (parents.isEmpty) "" else " extends " + parents.mkString(", "))) +
        (if (members.isEmpty) (if (isInGiven) " {}" else "") else " {\n" + members + "\n}")

    // TODO why some artifacts are not synthetic (e.g. in org.scalatest.funsuite.AnyFunSuiteLike)?
    // TODO why $default$ methods are not synthetic?
    case node @ Node(DEFDEF, Seq(name), children) if !node.hasFlag(FIELDaccessor) && !node.hasFlag(SYNTHETIC) && !node.hasFlag(ARTIFACT) && !name.contains("$default$") =>
      if (!privateMembers && node.hasFlag(PRIVATE)) return ""
      val isAbstractGiven = node.hasFlag(GIVEN)
      val isAnonymousGiven = isAbstractGiven && name.startsWith("given_")
      val isDeclaration = children.filter(!_.isModifier).lastOption.exists(_.isTypeTree)
      val tpe = children.dropWhile(_.is(TYPEPARAM, PARAM, EMPTYCLAUSE, SPLITCLAUSE)).headOption
      textOfAnnotationIn(node) +
      (if (name == "<init>") {
        modifiersIn(node) + "def this" + parametersIn(node) + " = ???" // TODO parameter, { /* compiled code */ }
      } else {
        (if (node.hasFlag(EXTENSION)) "extension " + parametersIn(node, target = Target.Extension) + "\n  " else "") +
          modifiersIn(node, (if (isAbstractGiven) Set(FINAL) else Set.empty), isParameter = false) + (if (isAbstractGiven) "" else "def ") +
          (if (isAnonymousGiven) "" else name) + parametersIn(node, target = if (node.hasFlag(EXTENSION)) Target.ExtensionMethod else Target.Definition) +
          ": " + tpe.map(it => simple(textOfType(it))).getOrElse("") + (if (isDeclaration) "" else " = ???") // TODO parameter, { /* compiled code */ }
      })

    case node @ Node(VALDEF, Seq(name), children) if !node.hasFlag(SYNTHETIC) && !node.hasFlag(OBJECT) =>
      if (!privateMembers && node.hasFlag(PRIVATE)) return ""
      val isDeclaration = children.filter(!_.isModifier).lastOption.exists(_.isTypeTree)
      val isCase = node.hasFlag(CASE)
      val isGivenAlias = node.hasFlag(GIVEN)
      val isAnonymousGiven = isGivenAlias && name.startsWith("given_") // TODO How to detect anonymous givens reliably?
      val tpe = children.headOption
      val template = // TODO check element types
        if (isCase) children.lift(1).flatMap(_.children.lift(1)).flatMap(_.children.headOption).map(textOf(_)).getOrElse("")
        else ""
      textOfAnnotationIn(node) +
      (if (isCase && !definition.exists(_.hasFlag(ENUM))) "" else modifiersIn(node, (if (isGivenAlias) Set(FINAL, LAZY) else Set.empty), isParameter = false) + (if (isCase) name +  template else
        (if (isGivenAlias) "" else (if (node.hasFlag(MUTABLE)) "var " else "val ")) +
          (if (isAnonymousGiven) "" else name + ": ") +
          tpe.map(it => simple(textOfType(it))).getOrElse("") + (if (isDeclaration) "" else " = ???"))) // TODO parameter, /* compiled code */

    case _ => "" // TODO exhaustive match
  }

  // TODO include in textOfType
  // TODO keep prefixes? but those are not "relative" imports, but regular (implicit) imports of each Scala compilation unit
  private def simple(tpe: String): String = {
    val s1 = tpe.stripPrefix("_root_.")
    val s2 = if (!s1.stripPrefix("scala.").takeWhile(!_.isWhitespace).stripSuffix(".type").contains('.')) s1.stripPrefix("scala.") else s1
    val s3 = if (!s2.stripPrefix("java.lang.").takeWhile(!_.isWhitespace).stripSuffix(".type").contains('.')) s2.stripPrefix("java.lang.") else s2
    val s4 = if (!s3.stripPrefix("scala.Predef.").takeWhile(!_.isWhitespace).stripSuffix(".type").contains('.')) s3.stripPrefix("scala.Predef.") else s3
    if (s4.nonEmpty) s4 else "Nothing" // TODO Remove when all types are supported
  }

  private def textOfType(node: Node, parensRequired: Boolean = false): String = node match { // TODO proper settings
    case Node(IDENTtpt, _, Seq(tail)) => textOfType(tail)
    case Node(SINGLETONtpt, _, Seq(tail)) =>
      val literal = textOfConstant(tail)
      if (literal.nonEmpty) literal else textOfType(tail) + ".type"
    case const @ Node(UNITconst | TRUEconst | FALSEconst | BYTEconst | SHORTconst | INTconst | LONGconst | FLOATconst | DOUBLEconst | CHARconst | STRINGconst | NULLconst, _, _: _*) => textOfConstant(const)
    case Node(TYPEREF, Seq(name), Seq(tail)) => textOfType(tail) + "." + name
    case Node(TERMREF, Seq(name), Seq(tail)) => if (name == "package") textOfType(tail) else textOfType(tail) + "." + name // TODO why there's "package" in rare cases?
    case Node(THIS, _, _) => "this" // TODO prefix
    case Node(TYPEREFsymbol | TYPEREFdirect | TERMREFsymbol | TERMREFdirect, _, _) => node.refName.getOrElse("") // TODO
    case Node(SELECTtpt | SELECT, Seq(name), Seq(tail)) =>
      if (Iterator.unfold(node)(_.children.headOption.map(it => (it, it))).exists(_.tag == THIS)) textOfType(tail) + "#" + name // TODO unify
      else {
        val qualifier = textOfType(tail)
        if (qualifier.nonEmpty) qualifier + "." + name else name
      }
    case Node(TERMREFpkg | TYPEREFpkg, Seq(name), _: _*) => name
    case Node(APPLIEDtpt, _, Seq(constructor, arguments: _*)) =>
      val (base, elements) = (textOfType(constructor), arguments.map(it => simple(textOfType(it))))
      if (base == "scala.&") elements.mkString(" & ") // TODO infix types in general?
      else if (base == "scala.|") elements.mkString(" | ")
      else {
        if (base.startsWith("scala.Tuple")) {
          elements.mkString("(", ", ", ")")
        } else if (base.startsWith("scala.Function")) {
          val s = (if (elements.length == 2) elements.head else elements.init.mkString("(", ", ", ")")) + " => " + elements.last
          if (parensRequired) "(" + s + ")" else s
        } else {
          simple(base) + "[" + elements.mkString(", ") + "]"
        }
      }
    case Node(ANNOTATEDtpt | ANNOTATEDtype, _, Seq(tpe, annotation)) =>
      annotation match {
        case Node(APPLY, _, Seq(Node(SELECTin, _, Seq(Node(NEW, _, Seq(tpe0, _: _*)), _: _*)), args: _*)) =>
          if (textOfType(tpe0) == "scala.annotation.internal.Repeated") textOfType(tpe.children(1)) + "*" // TODO check tree (APPLIEDtpt)
          else textOfType(tpe) + " " + "@" + simple(textOfType(tpe0)) + {
            val args = annotation.children.map(textOfConstant).filter(_.nonEmpty).mkString(", ")
            if (args.nonEmpty) "(" + args + ")" else ""
          }
        case _ => textOfType(tpe)
      }
    case Node(BYNAMEtpt, _, Seq(tpe)) => "=> " + simple(textOfType(tpe))

    case Node(TYPEBOUNDStpt, _, _) => "?" + boundsIn(node)

    case _ => "" // TODO exhaustive match
  }

  private def textOfConstant(node: Node): String = node.tag match {
    case UNITconst => "()"
    case TRUEconst => "true"
    case FALSEconst => "false"
    case BYTEconst | SHORTconst | INTconst => node.value.toString
    case LONGconst => node.value + "L"
    case FLOATconst => intBitsToFloat(node.value.toInt) + "F"
    case DOUBLEconst => longBitsToDouble(node.value) + "D"
    case CHARconst => "'" + node.value.toChar + "'"
    case STRINGconst => "\"" + node.name + "\""
    case NULLconst => "null"
    case _ => ""
  }

  private def textOfAnnotationIn(node: Node): String = {
    node.children.lastOption match {
      case Some(Node(ANNOTATION, _, Seq(tpe, Node(APPLY, _, children)))) =>
        val name = Option(tpe).map(textOfType(_)).filter(!_.startsWith("scala.annotation.internal.")).map(simple).map("@" + _).getOrElse("")
        if (name.isEmpty) "" else {
          val args = children.map(textOfConstant).filter(_.nonEmpty).mkString(", ")
          name + (if (args.nonEmpty) "(" + args + ")" else "") + "\n"
        }
      case _ => ""
    }
  }

  private def indent(s: String): String = s.split("\n").map(s => if (s.forall(_.isWhitespace)) "" else "  " + s).mkString("\n") // TODO use indent: Int parameter instead

  private enum Target {
    case Definition
    case Extension
    case ExtensionMethod
  }

  private def popExtensionParams(stack: mutable.Stack[Node]): Seq[Node] = {
    val buffer = mutable.Buffer[Node]()
    buffer ++= stack.popWhile(!_.is(PARAM))
    buffer += stack.pop()
    while (stack(0).is(SPLITCLAUSE) && stack(1).hasFlag(GIVEN)) {
      buffer += stack.pop()
      buffer ++= stack.popWhile(_.is(PARAM))
    }
    buffer.toSeq
  }

  private def parametersIn(node: Node, template: Option[Node] = None, definition: Option[Node] = None, target: Target = Target.Definition, modifiers: String = ""): String = {
    val tps = target match {
      case Target.Extension => node.children.takeWhile(!_.is(PARAM))
      case Target.ExtensionMethod => node.children.dropWhile(!_.is(PARAM))
      case Target.Definition => node.children
    }

    val templateTypeParams = template.map(_.children.filter(_.is(TYPEPARAM)).iterator)

    var params = ""
    var open = false
    var next = false

    tps.foreach {
      case node @ Node(TYPEPARAM, Seq(name), _: _*) =>
        if (!open) {
          params += "["
          open = true
          next = false
        }
        if (next) {
          params += ", "
        }
        params += textOfAnnotationIn(node).replace("\n", " ") // TODO Handle in the method
        templateTypeParams.map(_.next()).foreach { typeParam =>
          params += textOfAnnotationIn(typeParam).replace("\n", " ") // TODO Handle in the method
          if (typeParam.hasFlag(COVARIANT)) {
            params += "+"
          }
          if (typeParam.hasFlag(CONTRAVARIANT)) {
            params += "-"
          }
        }
        params += (if (name.startsWith("_$")) "_" else name) // TODO detect Unique name
        node.children match {
          case Seq(lambda @ Node(LAMBDAtpt, _, _: _*), _: _*) =>
            params += parametersIn(lambda)
          case Seq(bounds @ Node(TYPEBOUNDStpt, _, _: _*), _: _*) =>
            params += boundsIn(bounds)
          case _ =>
        }

        next = true
      case _ =>
    }
    if (open) params += "]"

    params += modifiers

    val ps = target match {
      case Target.Extension =>
        popExtensionParams(mutable.Stack[Node](node.children: _*))
      case Target.ExtensionMethod =>
        val stack = mutable.Stack[Node](node.children: _*)
        popExtensionParams(stack)
        stack.toSeq.dropWhile(_.is(SPLITCLAUSE))
      case Target.Definition => node.children
    }

    val templateValueParams = template.map(_.children.filter(_.is(PARAM)).iterator)

    open = false
    next = false

    ps.foreach {
      case Node(EMPTYCLAUSE, _, _) =>
        params += "()"
      case Node(SPLITCLAUSE, _, _) =>
        params += ")"
        open = false
      case node @ Node(PARAM, Seq(name), children) =>
        if (!open) {
          params += "("
          open = true
          next = false
          if (node.hasFlag(GIVEN)) {
            params += "using "
          }
          if (node.hasFlag(IMPLICIT)) {
            params += "implicit "
          }
        }
        if (next) {
          params += ", "
        }
        params += textOfAnnotationIn(node).replace("\n", " ") // TODO Handle in the method
        if (node.hasFlag(INLINE)) {
          params += "inline "
        }
        val templateValueParam = templateValueParams.map(_.next())
        if (!definition.exists(isGivenImplicitClass0)) {
          templateValueParam.foreach { valueParam =>
            if (!valueParam.hasFlag(LOCAL)) {
              params += modifiersIn(valueParam, Set(GIVEN))
              if (valueParam.hasFlag(MUTABLE)) {
                params += "var "
              } else {
                if (!(definition.exists(_.hasFlag(CASE)) && valueParam.flags.forall(_.is(CASEaccessor, HASDEFAULT)))) {
                  params += "val "
                }
              }
            }
          }
        }
        if (!(node.hasFlag(SYNTHETIC) || templateValueParam.exists(_.hasFlag(SYNTHETIC)))) {
          params += name + ": "
        }
        params += simple(textOfType(children.head))
        if (node.hasFlag(HASDEFAULT)) {
          params += " = ???" // TODO parameter, /* compiled code */
        }
        next = true
      case _ =>
    }
    if (open) params += ")"
    if (template.isEmpty || modifiers.nonEmpty || definition.exists(it => it.hasFlag(CASE) && !it.hasFlag(OBJECT))) params else params.stripSuffix("()")
  }

  private def modifiersIn(node: Node, excluding: Set[Int] = Set.empty, isParameter: Boolean = true): String = { // TODO Optimize
    var s = ""
    if (node.hasFlag(OVERRIDE)) {
      s += "override "
    }
    if (node.hasFlag(PRIVATE)) {
      if (node.hasFlag(LOCAL)) {
//        s += "private[this] " TODO Enable? (in Scala 3 it's almost always inferred)
        s += "private "
      } else {
        s += "private "
      }
    } else if (node.hasFlag(PROTECTED)) {
      s += "protected "
    } else {
      node.children.foreach {
        case Node(PRIVATEqualified, _, Seq(qualifier)) =>
          s += "private[" + asQualifier(textOfType(qualifier)) + "] "
        case Node(PROTECTEDqualified, _, Seq(qualifier)) =>
          s += "protected[" + asQualifier(textOfType(qualifier)) + "] "
        case _ =>
      }
    }
    if (node.hasFlag(SEALED) && !excluding(SEALED)) {
      s += "sealed "
    }
    if (node.hasFlag(OPEN)) {
      s += "open "
    }
    if (node.hasFlag(GIVEN) && !excluding(GIVEN)) {
      s += (if (isParameter) "using " else "given ")
    }
    if (node.hasFlag(IMPLICIT)) {
      s += "implicit "
    }
    if (node.hasFlag(FINAL) && !excluding(FINAL)) {
      s += "final "
    }
    if (node.hasFlag(LAZY) && !excluding(LAZY)) {
      s += "lazy "
    }
    if (node.hasFlag(ABSTRACT) && !excluding(ABSTRACT)) {
      s += "abstract "
    }
    if (node.hasFlag(TRANSPARENT)) {
      s += "transparent "
    }
    if (node.hasFlag(OPAQUE) && !excluding(OPAQUE)) {
      s += "opaque "
    }
    if (node.hasFlag(INLINE)) {
      s += "inline "
    }
    if (node.hasFlag(CASE) && !excluding(CASE)) {
      s += "case "
    }
    s
  }

  private def boundsIn(node: Node) = node match {
    case Node(TYPEBOUNDStpt, _, Seq(lower, upper)) =>
      var s = ""
      val l = simple(textOfType(lower))
      if (l.nonEmpty && l != "Nothing") {
        s += " >: " + l
      }
      val u = simple(textOfType(upper))
      if (u.nonEmpty && u != "Any") {
        s += " <: " + u
      }
      s
    case _ => "" // TODO exhaustive match
  }

  private def asQualifier(tpe: String): String = {
    val i = tpe.lastIndexOf(".")
    (if (i == -1) tpe else tpe.drop(i + 1)).stripSuffix("$")
  }
}
