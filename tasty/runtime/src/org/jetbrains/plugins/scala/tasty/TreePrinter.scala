package org.jetbrains.plugins.scala.tasty

import dotty.tools.tasty.TastyFormat.*

import java.lang.Double.longBitsToDouble
import java.lang.Float.intBitsToFloat
import scala.collection.mutable

// TODO refactor
// TODO use StringBuilder
class TreePrinter(privateMembers: Boolean = false) {

  private def isGivenObject0(typedef: Node): Boolean =
    typedef.hasFlag(OBJECT) && typedef.previousSibling.exists(prev => prev.is(VALDEF) && prev.hasFlag(OBJECT) && prev.hasFlag(GIVEN))

  private def isGivenImplicitClass0(typedef: Node): Boolean = {
    def isImplicitConversion(node: Node) = node.is(DEFDEF) && node.hasFlag(SYNTHETIC) && node.hasFlag(GIVEN) && node.name == typedef.name
    typedef.hasFlag(SYNTHETIC) &&
      (typedef.nextSibling.exists(isImplicitConversion) || typedef.nextSibling.exists(_.nextSibling.exists(_.nextSibling.exists(isImplicitConversion))))
  }

  def textOf(node: Node): String = {
    val sb = new StringBuilder()
    textOf(sb, node)
    sb.toString
  }

  private def textOf(sb: StringBuilder, node: Node, definition: Option[Node] = None): Unit = node match {
    case Node(PACKAGE, _, Seq(Node(TERMREFpkg, Seq(name), _), children: _*)) =>
      textOfPackage(sb, node, name, children)

    case node @ Node(TYPEDEF, _, _) if (!node.hasFlag(SYNTHETIC) || isGivenImplicitClass0(node)) && (privateMembers || !node.hasFlag(PRIVATE)) => // TODO why both are synthetic?
      textOfTypeDef(sb, node, definition)

    case node @ Node(DEFDEF, Seq(name), _) if !node.hasFlag(FIELDaccessor) && !node.hasFlag(SYNTHETIC) && !node.hasFlag(ARTIFACT) && !name.contains("$default$") && (privateMembers || !node.hasFlag(PRIVATE)) =>
      textOfDefDef(sb, node)

    case node @ Node(VALDEF, _, _) if !node.hasFlag(SYNTHETIC) && !node.hasFlag(OBJECT) && (privateMembers || !node.hasFlag(PRIVATE)) =>
      textOfValDef(sb, node, definition)

    case _ => "" // TODO exhaustive match
  }

  private def textOfPackage(sb: StringBuilder, node: Node, name: String, children: Seq[Node])(using privateMembers: Boolean = false): Unit = {
    children.filterNot(_.tag == IMPORT) match {
      case Seq(node @ Node(PACKAGE, _, _), _: _*) =>
        textOf(sb, node)

      case children =>
        val containsPackageObject = children match {
          case Seq(Node(VALDEF, Seq("package"), _: _*), Node(TYPEDEF, Seq("package$"), _), _: _*) => true // TODO use name type, not contents
          case _ => false
        }
        if (name != "<empty>") {
          sb ++= "package "
          if (containsPackageObject) {
            sb ++= name.split('.').init.mkString(".") // TODO optimize
          } else {
            sb ++= name
          }
          sb ++= "\n\n"

        }
        children match {
          case Seq(Node(VALDEF, Seq(name1), _: _*), Node(TYPEDEF, Seq(name2), Seq(template, _: _*)), _: _*) if (name1.endsWith("$package") && name2.endsWith("$package$")) => // TODO use name type, not contents
            template.children.filter(it => it.is(DEFDEF, VALDEF, TYPEDEF) && it.names != Seq("<init>")).foreach { definition =>
              val previousLength = sb.length
              textOf(sb, definition)
              if (sb.length > previousLength) {
                sb ++= "\n\n"
              }
            }
            if (sb.length >= 2 && sb.substring(sb.length - 2, sb.length) == "\n\n") {
              sb.delete(sb.length - 2, sb.length)
            }
          case _ =>
            children.foreach { child =>
              val previousLength = sb.length
              textOf(sb, child, if (containsPackageObject) Some(node) else None)
              if (sb.length > previousLength) {
                sb ++= "\n\n"
              }
            }
            if (sb.length >= 2 && sb.substring(sb.length - 2, sb.length) == "\n\n") {
              sb.delete(sb.length - 2, sb.length)
            }
        }
    }
  }

  private def textOfTypeDef(sb: StringBuilder, node: Node, definition: Option[Node] = None)(using privateMembers: Boolean = false): Unit = {
    val name = node.name
    val template = node.children.head
    val isEnum = node.hasFlag(ENUM)
    val isObject = node.hasFlag(OBJECT)
    val isGivenObject = isGivenObject0(node)
    val isImplicitClass = node.nextSibling.exists(it => it.is(DEFDEF) && it.hasFlag(SYNTHETIC) && it.hasFlag(IMPLICIT) && it.name == name)
    val isGivenImplicitClass = isGivenImplicitClass0(node)
    val isTypeMember = !template.is(TEMPLATE)
    val isAnonymousGiven = (isGivenObject || isGivenImplicitClass) && name.startsWith("given_") // TODO common method
    val isPackageObject = isObject && definition.exists(_.is(PACKAGE))
    sb ++= textOfAnnotationIn(node)
    sb ++= modifiersIn(if (isObject) node.previousSibling.getOrElse(node) else node,
      if (isGivenImplicitClass) Set(GIVEN) else (if (isEnum) Set(ABSTRACT, SEALED, CASE, FINAL) else (if (isTypeMember) Set.empty else Set(OPAQUE))), isParameter = false)
    if (isImplicitClass) {
      sb ++= "implicit "
    }
    if (isEnum) {
      if (node.hasFlag(CASE)) {
        sb ++= "case "
      } else {
        sb ++= "enum "
      }
    } else if (isObject) {
      if (!isGivenObject) {
        if (isPackageObject) {
          sb ++= "package object "
        } else {
          sb ++= "object "
        }
      }
    } else if (node.hasFlag(TRAIT)) {
      sb ++= "trait "
    } else if (isTypeMember) {
      sb ++= "type "
    } else if (isGivenImplicitClass) {
      sb ++= "given "
    } else {
      sb ++= "class "
    }
    if (!isAnonymousGiven) {
      if (isPackageObject) {
        sb ++= definition.get.children.headOption.flatMap(_.name.split('.').lastOption).getOrElse("") // TODO check
      } else {
        if (isObject) {
          sb ++= node.previousSibling.fold(name)(_.name) // TODO check type
        } else {
          sb ++= name
        }
      }
    }
    if (!isTypeMember) {
      textOfTemplate(sb, template, Some(node))
    } else {
      val repr = node.children.headOption.filter(_.is(LAMBDAtpt)).getOrElse(node) // TODO handle LAMBDAtpt in parametersIn?
      val bounds = repr.children.find(_.is(TYPEBOUNDStpt))
      sb ++= parametersIn(repr, Some(repr))
      if (bounds.isDefined) {
        sb ++= boundsIn(bounds.get)
      } else {
        sb ++= " = "
        if (node.hasFlag(OPAQUE)) {
          sb ++= "???" // TODO parameter, { /* compiled code */ }
        } else {
          repr.children.findLast(_.isTypeTree) match {
            case Some(t) =>
              sb ++= simple(textOfType(t))
            case None =>
              sb ++= simple("") // TODO
          }
        }
      }
    }
  }

  // TODO why some artifacts are not synthetic (e.g. in org.scalatest.funsuite.AnyFunSuiteLike)?
  // TODO why $default$ methods are not synthetic?
  private def textOfTemplate(sb: StringBuilder, node: Node, definition: Option[Node])(using privateMembers: Boolean = false): Unit = {
    val children = node.children
    val primaryConstructor = children.find(it => it.is(DEFDEF) && it.names == Seq("<init>"))
    val modifiers = primaryConstructor.map(modifiersIn(_)).map(it => if (it.nonEmpty) " " + it else "").getOrElse("")
    val isInEnum = definition.exists(_.hasFlag(ENUM))
    val isInCaseClass = !isInEnum && definition.exists(_.hasFlag(CASE))
    val parents = children.collect { // TODO rely on name kind
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

    val previousLength = sb.length
    primaryConstructor.foreach { constructor =>
      sb ++= parametersIn(constructor, Some(node), definition, modifiers = modifiers)
    }
    val hasParameters = sb.length > previousLength
    if (isInGiven && (!isInAnonymousGiven || hasParameters)) {
      sb ++= ": "
    }
    if (isInGiven) {
      sb ++= parents.mkString(" with ") + " with"
    } else {
      if (parents.nonEmpty) {
        sb ++= " extends " + parents.mkString(", ")
      }
    }
    val members = {
      val cases = // TODO check element types
        if (isInEnum) definition.get.nextSibling.get.nextSibling.get.children.head.children.filter(it => it.is(VALDEF) || it.is(TYPEDEF))
        else Seq.empty

      children.filter(it => it.is(DEFDEF, VALDEF, TYPEDEF) && !primaryConstructor.contains(it)) ++ cases // TODO type member
    }
    if (members.nonEmpty) {
      sb ++= " {\n"
      val previousLength = sb.length
      var isFirst = true
      members.foreach { member =>
        val sb1 = new StringBuilder() // TODO
        textOf(sb1, member, definition)
        val text = sb1.toString
        if (text.nonEmpty) { // TODO optimize
          if (isFirst) {
            isFirst = false
          } else {
            sb ++= "\n\n"
          }
          // TODO use indent: Int parameter instead
          def indent(s: String): String = s.split("\n").map(s => if (s.forall(_.isWhitespace)) "" else "  " + s).mkString("\n")
          val indentedText = indent(text)
          if (definition.exists(_.hasFlag(ENUM))) {
            sb ++= indentedText.stripSuffix(" extends " + definition.map(_.name).getOrElse("")) // TODO not text-based (need to know an outer definition)
          } else {
            sb ++= indentedText
          }
        }
      }
      if (sb.length > previousLength) {
        sb ++= "\n}"
      } else {
        sb.delete(previousLength - 3, previousLength)
      }
    } else {
      if (isInGiven) {
        sb ++= " {}"
      }
    }
  }

  private def textOfDefDef(sb: StringBuilder, node: Node): Unit = {
    sb ++= textOfAnnotationIn(node)
    val name = node.name
    if (name == "<init>") {
      sb ++= modifiersIn(node)
      sb ++= "def this"
      sb ++= parametersIn(node)
      sb ++= " = ???" // TODO parameter, { /* compiled code */ }
    } else {
      if (node.hasFlag(EXTENSION)) {
        sb ++= "extension "
        sb ++= parametersIn(node, target = Target.Extension)
        sb ++= "\n  "
      }
      val isAbstractGiven = node.hasFlag(GIVEN)
      sb ++= modifiersIn(node, (if (isAbstractGiven) Set(FINAL) else Set.empty), isParameter = false)
      if (!isAbstractGiven) {
        sb ++= "def "
      }
      val isAnonymousGiven = isAbstractGiven && name.startsWith("given_")
      if (!isAnonymousGiven) {
        sb ++= name
      }
      sb ++= parametersIn(node, target = if (node.hasFlag(EXTENSION)) Target.ExtensionMethod else Target.Definition)
      sb ++= ": "
      val children = node.children
      val tpe = children.dropWhile(_.is(TYPEPARAM, PARAM, EMPTYCLAUSE, SPLITCLAUSE)).headOption
      tpe match {
        case Some(t) =>
          sb ++= simple(textOfType(t))
        case None =>
          sb ++= simple("") // TODO
      }
      val isDeclaration = children.filter(!_.isModifier).lastOption.exists(_.isTypeTree)
      if (!isDeclaration) {
        sb ++= " = ???" // TODO parameter, { /* compiled code */ }
      }
    }
  }

  private def textOfValDef(sb: StringBuilder, node: Node, definition: Option[Node] = None)(using privateMembers: Boolean = false): Unit = {
    val isCase = node.hasFlag(CASE)
    sb ++= textOfAnnotationIn(node)
    if (!isCase || definition.exists(_.hasFlag(ENUM))) {
      val name = node.name
      val children = node.children
      val isGivenAlias = node.hasFlag(GIVEN)
      sb ++= modifiersIn(node, (if (isGivenAlias) Set(FINAL, LAZY) else Set.empty), isParameter = false)
      if (isCase) {
        sb ++= name
        if (isCase) {
          // TODO check element types
          children.lift(1).flatMap(_.children.lift(1)).flatMap(_.children.headOption).foreach { template =>
            textOfTemplate(sb, template, None)
          }
        }
      } else {
        if (!isGivenAlias) {
          if (node.hasFlag(MUTABLE)) {
            sb ++= "var "
          } else {
            sb ++= "val "
          }
        }
        val isAnonymousGiven = isGivenAlias && name.startsWith("given_") // TODO How to detect anonymous givens reliably?
        if (!isAnonymousGiven) {
          sb ++= name + ": "
        }
        val tpe = children.headOption
        tpe match {
          case Some(t) =>
            sb ++= simple(textOfType(t))
          case None =>
            sb ++= simple("") // TODO
        }
        val isDeclaration = children.filter(!_.isModifier).lastOption.exists(_.isTypeTree)
        if (!isDeclaration) {
          sb ++= " = ???" // TODO parameter, /* compiled code */
        }
      }
    }
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
    case Node(TERMREF, Seq(name), Seq(tail)) => if (name == "package" || name.endsWith("$package")) textOfType(tail) else textOfType(tail) + "." + name // TODO why there's "package" in some cases?
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
        } else if (base.startsWith("scala.Function") || base.startsWith("scala.ContextFunction")) {
          val arrow = if (base.startsWith("scala.Function")) " => " else " ?=> "
          val s = (if (elements.length == 2) elements.head else elements.init.mkString("(", ", ", ")")) + arrow + elements.last
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

    case Node(LAMBDAtpt, _, children) => parametersIn(node) + " =>> " + children.lastOption.map(textOfType(_)).getOrElse("") // TODO check tree

    case Node(REFINEDtpt, _, Seq(tr @ Node(TYPEREF, _, _), Node(DEFDEF, Seq(name), children), _ : _*)) if textOfType(tr) == "scala.PolyFunction" && name == "apply" => // TODO check tree
      val (typeParams, tail1) = children.span(_.is(TYPEPARAM))
      val (valueParams, tails2) = tail1.span(_.is(PARAM))
      typeParams.map(_.name).mkString("[", ", ", "]") + " => " + {
        val params = valueParams.flatMap(_.children.headOption.map(tpe => simple(textOfType(tpe)))).mkString(", ")
        if (valueParams.length == 1) params else "(" + params + ")"
      } + " => " + tails2.headOption.map(tpe => simple(textOfType(tpe))).getOrElse("")

    case Node(REFINEDtpt, _, Seq(tpe, members: _*)) =>
      val prefix = textOfType(tpe)
      (if (prefix == "java.lang.Object") "" else simple(prefix) + " ") + "{ " + members.map(textOf(_)).mkString("; ") + " }" // TODO textOfMember

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
        if (template.isEmpty) { // TODO deduplicate
          if (node.hasFlag(COVARIANT)) {
            params += "+"
          }
          if (node.hasFlag(CONTRAVARIANT)) {
            params += "-"
          }
        }
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
            lambda.children.lastOption match { // TODO deduplicate somehow?
              case Some(bounds @ Node(TYPEBOUNDStpt, _, _: _*)) =>
                params += boundsIn(bounds)
              case _ =>
            }
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
