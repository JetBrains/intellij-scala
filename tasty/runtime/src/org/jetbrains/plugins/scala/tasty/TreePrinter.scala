package org.jetbrains.plugins.scala.tasty

import dotty.tools.tasty.TastyBuffer.Addr
import dotty.tools.tasty.TastyFormat.*
import org.jetbrains.plugins.scala.tasty.Node.{Node1, Node2, Node3}

import java.lang.Double.longBitsToDouble
import java.lang.Float.intBitsToFloat
import scala.annotation.tailrec
import scala.collection.mutable

// TODO refactor
// TODO use StringBuilder: type
// TODO nonEmpty predicate
// TODO implicit StringBuilder?
// TODO indent: opaque type, implicit
class TreePrinter(privateMembers: Boolean = false) {
  private final val Indent = "  "

  // TODO use parameters
  private val sharedTypes = mutable.Map[Addr, String]()
  private val sourceFiles = mutable.Buffer[String]()

  private def isGivenObject0(typedef: Node): Boolean =
    typedef.contains(OBJECT) && typedef.previousSibling.exists(prev => prev.is(VALDEF) && prev.contains(OBJECT) && prev.contains(GIVEN))

  private def isGivenImplicitClass0(typedef: Node): Boolean = {
    def isImplicitConversion(node: Node) = node.is(DEFDEF) && node.contains(SYNTHETIC) && node.contains(GIVEN) && node.name == typedef.name
    typedef.contains(SYNTHETIC) &&
      (typedef.nextSibling.exists(isImplicitConversion) || typedef.nextSibling.exists(_.nextSibling.exists(_.nextSibling.exists(isImplicitConversion))))
  }

  def fileAndTextOf(node: Node): (String, String) = {
    assert(sharedTypes.isEmpty)
    assert(sourceFiles.isEmpty)
    val sb = new StringBuilder(1024 * 8)
    textOfPackage(sb, "", node)
    (sourceFiles.headOption.getOrElse("Unknown.scala"), sb.toString)
  }

  // TODO partial function, no prefix (or before & after functions)?
  @tailrec private def textOfPackage(sb: StringBuilder, indent: String, node: Node, definition: Option[Node] = None, prefix: String = ""): Unit = node match {
    case Node3(PACKAGE, _, Seq(Node3(TERMREFpkg, Seq(name), _), children: _*)) =>
      children.filterNot(_.tag == IMPORT) match {
        case Seq(node @ Node1(PACKAGE), _: _*) =>
          textOfPackage(sb, indent, node)

        case children =>
          val containsPackageObject = children match {
            case Seq(Node2(VALDEF, Seq("package")), Node2(TYPEDEF, Seq("package$")), _: _*) => true // TODO use name type, not contents
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
          // TODO extract method, de-duplicate
          var delimiterRequired = false
          children match {
            case Seq(Node2(VALDEF, Seq(name1)), tpe @ Node3(TYPEDEF, Seq(name2), Seq(template, _: _*)), _: _*) if name1.endsWith("$package") && name2.endsWith("$package$") => // TODO use name type, not contents
              readSourceFileAnnotationIn(tpe)
              template.children.filter(it => it.is(DEFDEF, VALDEF, TYPEDEF) && it.names != Seq("<init>")).foreach { definition =>
                val previousLength = sb.length
                textOfMember(sb, indent, definition, None, if (delimiterRequired) "\n\n" else "")
                delimiterRequired = delimiterRequired || sb.length > previousLength
              }
            case _ =>
              children.foreach { child =>
                val previousLength = sb.length
                textOfMember(sb, indent, child, if (containsPackageObject) Some(node) else None, if (delimiterRequired) "\n\n" else "")
                delimiterRequired = delimiterRequired || sb.length > previousLength
              }
          }
      }

    case _ =>
      textOfMember(sb, indent, node, definition, prefix)
  }

  private def textOfMember(sb: StringBuilder, indent: String, node: Node, definition: Option[Node] = None, prefix: String = ""): Unit = node match {
    case node @ Node1(TYPEDEF) if (privateMembers || !node.contains(PRIVATE)) && (!node.contains(SYNTHETIC) || isGivenImplicitClass0(node)) => // TODO why both are synthetic?
      sb ++= prefix
      textOfTypeDef(sb, indent, node, definition)

    case node @ Node2(DEFDEF, Seq(name)) if (privateMembers || !node.contains(PRIVATE)) && !node.contains(SYNTHETIC) && !node.contains(FIELDaccessor) && !node.contains(ARTIFACT) && !name.contains("$default$") =>
      sb ++= prefix
      textOfDefDef(sb, indent: String, node)

    case node @ Node1(VALDEF) if (privateMembers || !node.contains(PRIVATE)) && !node.contains(SYNTHETIC) && !node.contains(OBJECT) && (!node.contains(CASE) || definition.exists(_.contains(ENUM))) =>
      sb ++= prefix
      textOfValDef(sb, indent, node, definition)

    case _ => // TODO exhaustive match
  }

  private def textOfTypeDef(sb: StringBuilder, indent: String, node: Node, definition: Option[Node] = None): Unit = {
    val name = node.name
    val template = node.children.head
    val isEnum = node.contains(ENUM)
    val isObject = node.contains(OBJECT)
    val isGivenObject = isGivenObject0(node)
    val isImplicitClass = node.nextSibling.exists(it => it.is(DEFDEF) && it.contains(SYNTHETIC) && it.contains(IMPLICIT) && it.name == name)
    val isGivenImplicitClass = isGivenImplicitClass0(node)
    val isTypeMember = !template.is(TEMPLATE)
    val isAnonymousGiven = (isGivenObject || isGivenImplicitClass) && name.startsWith("given_") // TODO common method
    val isPackageObject = isObject && definition.exists(_.is(PACKAGE))
    readSourceFileAnnotationIn(node)
    textOfAnnotationIn(sb, indent, node, "\n")
    sb ++= indent
    modifiersIn(sb, if (isObject) node.previousSibling.getOrElse(node) else node,
      if (isGivenImplicitClass) Set(GIVEN) else (if (isEnum) Set(ABSTRACT, SEALED, CASE, FINAL) else (if (isTypeMember) Set.empty else Set(OPAQUE))), isParameter = false)
    if (isImplicitClass) {
      sb ++= "implicit "
    }
    if (isEnum) {
      if (node.contains(CASE)) {
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
    } else if (node.contains(TRAIT)) {
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
      textOfTemplate(sb, indent, template, Some(node))
    } else {
      val repr = node.children.headOption.filter(_.is(LAMBDAtpt)).getOrElse(node) // TODO handle LAMBDAtpt in parametersIn?
      val bounds = repr.children.find(_.is(TYPEBOUNDStpt))
      parametersIn(sb, repr, Some(repr))
      if (bounds.isDefined) {
        boundsIn(sb, bounds.get)
      } else {
        sb ++= " = "
        if (node.contains(OPAQUE)) {
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
  private def textOfTemplate(sb: StringBuilder, indent: String, node: Node, definition: Option[Node]): Unit = {
    val children = node.children
    val primaryConstructor = children.find(it => it.is(DEFDEF) && it.names == Seq("<init>"))
    val isInEnum = definition.exists(_.contains(ENUM))
    val isInCaseClass = !isInEnum && definition.exists(_.contains(CASE))
    val parents = children.collect { // TODO rely on name kind
      case node if node.isTypeTree => node
      case Node3(APPLY, _, Seq(Node3(SELECTin, _, Seq(Node3(NEW, _, Seq(tpe, _: _*)), _: _*)), _: _*)) => tpe
      case Node3(APPLY, _, Seq(Node3(APPLY, _, Seq(Node3(SELECTin, _, Seq(Node3(NEW, _, Seq(tpe, _: _*)), _: _*)), _: _*)), _: _*)) => tpe
      case Node3(APPLY, _, Seq(Node3(TYPEAPPLY, _, Seq(Node3(SELECTin, _, Seq(Node3(NEW, _, Seq(tpe, _: _*)), _: _*)), _: _*)), _: _*)) => tpe
      case Node3(APPLY, _, Seq(Node3(APPLY, _, Seq(Node3(TYPEAPPLY, _, Seq(Node3(SELECTin, _, Seq(Node3(NEW, _, Seq(tpe, _: _*)), _: _*)), _: _*)), _: _*)), _: _*)) => tpe
    }.map(textOfType(_, parensRequired = true))
      .filter(s => s.nonEmpty && s != "java.lang.Object" && s != "_root_.scala.runtime.EnumValue" &&
        !(isInCaseClass && s == "_root_.scala.Product" || s == "_root_.scala.Serializable"))
      .map(simple)
    val isInGiven = definition.exists(it => isGivenObject0(it) || isGivenImplicitClass0(it))
    val isInAnonymousGiven = isInGiven && definition.exists(_.name.startsWith("given_")) // TODO common method

    val previousLength = sb.length
    primaryConstructor.foreach { constructor =>
      val sb1 = new StringBuilder() // TODO
      modifiersIn(sb1, constructor)
      val modifiers = if (sb1.nonEmpty) " " + sb1.toString else ""
      parametersIn(sb, constructor, Some(node), definition, modifiers = _ ++= modifiers)
    }
    val hasParameters = sb.length > previousLength
    if (isInGiven && (!isInAnonymousGiven || hasParameters)) {
      sb ++= ": "
    }
    if (isInGiven) {
      sb ++= parents.mkString(" with ") + " with"
    } else {
      if (parents.nonEmpty && !(parents.length == 1 && !parents.head.endsWith("]") && (definition.isEmpty || definition.exists(it => it.contains(ENUM) && it.contains(CASE))))) {
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
      var delimiterRequired = false
      members.foreach { member =>
        val previousLength = sb.length
        textOfMember(sb, indent + Indent, member, definition, if (delimiterRequired) "\n\n" else "")
        delimiterRequired = delimiterRequired || sb.length > previousLength
      }
      if (sb.length > previousLength) {
        sb ++= "\n"
        sb ++= indent
        sb ++= "}"
      } else {
        sb.delete(previousLength - 3, previousLength)
      }
    } else {
      if (isInGiven) {
        sb ++= " {}"
      }
    }
  }

  private def textOfDefDef(sb: StringBuilder, indent: String, node: Node): Unit = {
    if (!node.contains(EXTENSION)) {
      textOfAnnotationIn(sb, indent, node, "\n")
    }
    sb ++= indent
    val name = node.name
    if (name == "<init>") {
      modifiersIn(sb, node)
      sb ++= "def this"
      parametersIn(sb, node)
      sb ++= " = ???" // TODO parameter, { /* compiled code */ }
    } else {
      if (node.contains(EXTENSION)) {
        sb ++= "extension "
        parametersIn(sb, node, target = Target.Extension)
        sb ++= "\n"
        textOfAnnotationIn(sb, indent + Indent, node, "\n")
        sb ++= indent
        sb ++= Indent
      }
      val isAbstractGiven = node.contains(GIVEN)
      modifiersIn(sb, node, (if (isAbstractGiven) Set(FINAL) else Set.empty), isParameter = false)
      if (!isAbstractGiven) {
        sb ++= "def "
      }
      val isAnonymousGiven = isAbstractGiven && name.startsWith("given_")
      if (!isAnonymousGiven) {
        sb ++= name
      }
      parametersIn(sb, node, target = if (node.contains(EXTENSION)) Target.ExtensionMethod else Target.Definition)
      sb ++= ": "
      val remainder = node.children.dropWhile(_.is(TYPEPARAM, PARAM, EMPTYCLAUSE, SPLITCLAUSE))
      val tpe = remainder.headOption
      tpe match {
        case Some(t) =>
          sb ++= simple(textOfType(t))
        case None =>
          sb ++= simple("") // TODO
      }
      val isDeclaration = remainder.drop(1).forall(_.isModifier)
      if (!isDeclaration) {
        sb ++= " = ???" // TODO parameter, { /* compiled code */ }
      }
    }
  }

  private def textOfValDef(sb: StringBuilder, indent: String, node: Node, definition: Option[Node] = None): Unit = {
    textOfAnnotationIn(sb, indent, node, "\n")
    sb ++= indent
    val name = node.name
    val children = node.children
    val isGivenAlias = node.contains(GIVEN)
    modifiersIn(sb, node, (if (isGivenAlias) Set(FINAL, LAZY) else Set.empty), isParameter = false)
    val isCase = node.contains(CASE)
    if (isCase) {
      sb ++= name
      if (isCase) {
        // TODO check element types
        children.lift(1).flatMap(_.children.lift(1)).flatMap(_.children.headOption).foreach { template =>
          textOfTemplate(sb, indent, template, None)
        }
      }
    } else {
      if (!isGivenAlias) {
        if (node.contains(MUTABLE)) {
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
      val isDeclaration = children.drop(1).forall(_.isModifier)
      if (!isDeclaration) {
        sb ++= " = ???" // TODO parameter, /* compiled code */
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

  private def textOfType(node: Node, parensRequired: Boolean = false): String = {
    if (node.isSharedType) {
      sharedTypes.get(node.addr) match {
        case Some(text) =>
          return text
        case _ =>
      }
    }
    // TODO extract method
    val text = node match { // TODO proper settings
      case Node3(IDENTtpt, _, Seq(tail)) => textOfType(tail)
      case Node3(SINGLETONtpt, _, Seq(tail)) =>
        val literal = textOfConstant(tail)
        if (literal.nonEmpty) literal else textOfType(tail) + ".type"
      case const @ Node1(UNITconst | TRUEconst | FALSEconst | BYTEconst | SHORTconst | INTconst | LONGconst | FLOATconst | DOUBLEconst | CHARconst | STRINGconst | NULLconst) => textOfConstant(const)
      case Node3(TYPEREF, Seq(name), Seq(tail)) => textOfType(tail) + "." + name
      case Node3(TERMREF, Seq(name), Seq(tail)) => if (name == "package" || name.endsWith("$package")) textOfType(tail) else textOfType(tail) + "." + name // TODO why there's "package" in some cases?
      case Node1(THIS) => "this" // TODO prefix
      case Node1(TYPEREFsymbol | TYPEREFdirect | TERMREFsymbol | TERMREFdirect) => node.refName.getOrElse("") // TODO
      case Node3(SELECTtpt | SELECT, Seq(name), Seq(tail)) =>
        if (Iterator.unfold(node)(_.children.headOption.map(it => (it, it))).exists(_.tag == THIS)) textOfType(tail) + "#" + name // TODO unify
        else {
          val qualifier = textOfType(tail)
          if (qualifier.nonEmpty) qualifier + "." + name else name
        }
      case Node2(TERMREFpkg | TYPEREFpkg, Seq(name)) => name
      case Node3(APPLIEDtpt, _, Seq(constructor, arguments: _*)) =>
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
      case Node3(ANNOTATEDtpt | ANNOTATEDtype, _, Seq(tpe, annotation)) =>
        annotation match {
          case Node3(APPLY, _, Seq(Node3(SELECTin, _, Seq(Node3(NEW, _, Seq(tpe0, _: _*)), _: _*)), args: _*)) =>
            if (textOfType(tpe0) == "scala.annotation.internal.Repeated") textOfType(tpe.children(1)) + "*" // TODO check tree (APPLIEDtpt)
            else textOfType(tpe) + " " + "@" + simple(textOfType(tpe0)) + {
              val args = annotation.children.map(textOfConstant).filter(_.nonEmpty).mkString(", ")
              if (args.nonEmpty) "(" + args + ")" else ""
            }
          case _ => textOfType(tpe)
        }
      case Node3(BYNAMEtpt, _, Seq(tpe)) => "=> " + simple(textOfType(tpe))

      case Node1(TYPEBOUNDStpt) =>
        val sb1 = new StringBuilder() // TODO
        boundsIn(sb1, node)
        "?" + sb1.toString

      case Node3(LAMBDAtpt, _, children) =>
        val sb1 = new StringBuilder() // TODO
        parametersIn(sb1, node)
        sb1.toString + " =>> " + children.lastOption.map(textOfType(_)).getOrElse("") // TODO check tree

      case Node3(REFINEDtpt, _, Seq(tr @ Node1(TYPEREF), Node3(DEFDEF, Seq(name), children), _ : _*)) if textOfType(tr) == "scala.PolyFunction" && name == "apply" => // TODO check tree
        val (typeParams, tail1) = children.span(_.is(TYPEPARAM))
        val (valueParams, tails2) = tail1.span(_.is(PARAM))
        typeParams.map(_.name).mkString("[", ", ", "]") + " => " + {
          val params = valueParams.flatMap(_.children.headOption.map(tpe => simple(textOfType(tpe)))).mkString(", ")
          if (valueParams.length == 1) params else "(" + params + ")"
        } + " => " + tails2.headOption.map(tpe => simple(textOfType(tpe))).getOrElse("")

      case Node3(REFINEDtpt, _, Seq(tpe, members: _*)) =>
        val prefix = textOfType(tpe)
        (if (prefix == "java.lang.Object") "" else simple(prefix) + " ") + "{ " + members.map(it => { val sb = new StringBuilder(); textOfMember(sb, "", it); sb.toString }).mkString("; ") + " }" // TODO use sb directly

      case _ => "" // TODO exhaustive match
    }
    sharedTypes.put(node.addr, text)
    text
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

  private def textOfAnnotationIn(sb: StringBuilder, indent: String, node: Node, suffix: String): Unit = {
    node.children.reverseIterator.takeWhile(_.is(ANNOTATION)).foreach {  // TODO sb.insert?
      case Node3(ANNOTATION, _, Seq(tpe, apply @ Node1(APPLY))) =>
        val name = Option(tpe).map(textOfType(_)).filter(!_.startsWith("scala.annotation.internal.")).map(simple).map("@" + _).getOrElse("") // TODO optimize
        if (name.nonEmpty) {
          sb ++= indent
          sb ++= name
          val args = apply.children.map(textOfConstant).filter(_.nonEmpty).mkString(", ") // TODO optimize
          if (args.nonEmpty) {
            sb ++= "("
            sb ++= args
            sb ++= ")"
          }
          sb ++= suffix
        }
      case _ =>
    }
  }

  private def readSourceFileAnnotationIn(node: Node): Unit = {
    node.children.reverseIterator.takeWhile(_.is(ANNOTATION)).foreach {
      case Node3(ANNOTATION, _, Seq(tpe, apply@Node1(APPLY))) if (textOfType(tpe) == "scala.annotation.internal.SourceFile") =>
        apply.children.lastOption.map(_.name).foreach { path =>
          val i = path.replace('\\', '/').lastIndexOf("/")
          sourceFiles += (if (i > 0) path.substring(i + 1) else path)
        }
      case _ =>
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
    while (stack(0).is(SPLITCLAUSE) && stack(1).contains(GIVEN)) {
      buffer += stack.pop()
      buffer ++= stack.popWhile(_.is(PARAM))
    }
    buffer.toSeq
  }

  private def parametersIn(sb: StringBuilder, node: Node, template: Option[Node] = None, definition: Option[Node] = None, target: Target = Target.Definition, modifiers: StringBuilder => Unit = _ => ()): Unit = {
    val tps = target match {
      case Target.Extension => node.children.takeWhile(!_.is(PARAM))
      case Target.ExtensionMethod => node.children.dropWhile(!_.is(PARAM))
      case Target.Definition => node.children
    }

    val templateTypeParams = template.map(_.children.filter(_.is(TYPEPARAM)).iterator)

    var open = false
    var next = false

    tps.foreach {
      case node @ Node2(TYPEPARAM, Seq(name)) =>
        if (!open) {
          sb ++= "["
          open = true
          next = false
        }
        if (next) {
          sb ++= ", "
        }
        textOfAnnotationIn(sb, "", node, " ")
        if (template.isEmpty) { // TODO deduplicate
          if (node.contains(COVARIANT)) {
            sb ++= "+"
          }
          if (node.contains(CONTRAVARIANT)) {
            sb ++= "-"
          }
        }
        templateTypeParams.map(_.next()).foreach { typeParam =>
          textOfAnnotationIn(sb, "", typeParam, " ")
          if (typeParam.contains(COVARIANT)) {
            sb ++= "+"
          }
          if (typeParam.contains(CONTRAVARIANT)) {
            sb ++= "-"
          }
        }
        sb ++= (if (name.startsWith("_$")) "_" else name) // TODO detect Unique name
        node.children match {
          case Seq(lambda @ Node1(LAMBDAtpt), _: _*) =>
            parametersIn(sb, lambda)
            lambda.children.lastOption match { // TODO deduplicate somehow?
              case Some(bounds @ Node1(TYPEBOUNDStpt)) =>
                boundsIn(sb, bounds)
              case _ =>
            }
          case Seq(bounds @ Node1(TYPEBOUNDStpt), _: _*) =>
            boundsIn(sb, bounds)
          case _ =>
        }
        next = true
      case _ =>
    }
    if (open) {
      sb ++= "]"
    }

    val previousLength = sb.length
    modifiers(sb)
    val hasModifiers = sb.length > previousLength

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
      case Node1(EMPTYCLAUSE) =>
        sb ++= "()"
      case Node1(SPLITCLAUSE) =>
        sb ++= ")"
        open = false
      case node @ Node3(PARAM, Seq(name), children) =>
        if (!open) {
          sb ++= "("
          open = true
          next = false
          if (node.contains(GIVEN)) {
            sb ++= "using "
          }
          if (node.contains(IMPLICIT)) {
            sb ++= "implicit "
          }
        }
        if (next) {
          sb ++= ", "
        }
        textOfAnnotationIn(sb, "", node, " ")
        if (node.contains(INLINE)) {
          sb ++= "inline "
        }
        val templateValueParam = templateValueParams.map(_.next())
        if (!definition.exists(isGivenImplicitClass0)) {
          templateValueParam.foreach { valueParam =>
            if (!valueParam.contains(LOCAL)) {
              val sb1 = new StringBuilder() // TODO
              modifiersIn(sb1, valueParam, Set(GIVEN))
              sb ++= sb1
              if (valueParam.contains(MUTABLE)) {
                sb ++= "var "
              } else {
                if (!(definition.exists(_.contains(CASE)) && valueParam.modifierTags.forall(it => it == CASEaccessor || it == HASDEFAULT))) {
                  sb ++= "val "
                }
              }
            }
          }
        }
        if (!(node.contains(SYNTHETIC) || templateValueParam.exists(_.contains(SYNTHETIC)))) {
          sb ++= name + ": "
        }
        sb ++= simple(textOfType(children.head))
        if (node.contains(HASDEFAULT)) {
          sb ++= " = ???" // TODO parameter, /* compiled code */
        }
        next = true
      case _ =>
    }
    if (open) {
      sb ++= ")"
    }
    if (template.isEmpty || hasModifiers || definition.exists(it => it.contains(CASE) && !it.contains(OBJECT))) {} else {
      if (sb.length >= 2 && sb.substring(sb.length - 2, sb.length()) == "()") {
        sb.delete(sb.length - 2, sb.length())
      }
    }
  }

  private def modifiersIn(sb: StringBuilder, node: Node, excluding: Set[Int] = Set.empty, isParameter: Boolean = true): Unit = { // TODO Optimize
    if (node.contains(OVERRIDE)) {
      sb ++= "override "
    }
    if (node.contains(PRIVATE)) {
      if (node.contains(LOCAL)) {
//        sb += "private[this] " TODO Enable? (in Scala 3 it's almost always inferred)
        sb ++= "private "
      } else {
        sb ++= "private "
      }
    } else if (node.contains(PROTECTED)) {
      sb ++= "protected "
    } else {
      node.children.foreach {
        case Node3(PRIVATEqualified, _, Seq(qualifier)) =>
          sb ++= "private[" + asQualifier(textOfType(qualifier)) + "] "
        case Node3(PROTECTEDqualified, _, Seq(qualifier)) =>
          sb ++= "protected[" + asQualifier(textOfType(qualifier)) + "] "
        case _ =>
      }
    }
    if (node.contains(SEALED) && !excluding(SEALED)) {
      sb ++= "sealed "
    }
    if (node.contains(OPEN)) {
      sb ++= "open "
    }
    if (node.contains(GIVEN) && !excluding(GIVEN)) {
      sb ++= (if (isParameter) "using " else "given ")
    }
    if (node.contains(IMPLICIT)) {
      sb ++= "implicit "
    }
    if (node.contains(FINAL) && !excluding(FINAL)) {
      sb ++= "final "
    }
    if (node.contains(LAZY) && !excluding(LAZY)) {
      sb ++= "lazy "
    }
    if (node.contains(ABSTRACT) && !excluding(ABSTRACT)) {
      sb ++= "abstract "
    }
    if (node.contains(TRANSPARENT)) {
      sb ++= "transparent "
    }
    if (node.contains(OPAQUE) && !excluding(OPAQUE)) {
      sb ++= "opaque "
    }
    if (node.contains(INLINE)) {
      sb ++= "inline "
    }
    if (node.contains(CASE) && !excluding(CASE)) {
      sb ++= "case "
    }
  }

  private def boundsIn(sb: StringBuilder, node: Node): Unit = node match {
    case Node3(TYPEBOUNDStpt, _, Seq(lower, upper)) =>
      val l = simple(textOfType(lower))
      if (l.nonEmpty && l != "Nothing") { // TODO use FQNs
        sb ++= " >: " + l
      }
      val u = simple(textOfType(upper))
      if (u.nonEmpty && u != "Any") {
        sb ++= " <: " + u
      }
    case _ => "" // TODO exhaustive match
  }

  private def asQualifier(tpe: String): String = {
    val i = tpe.lastIndexOf(".")
    (if (i == -1) tpe else tpe.drop(i + 1)).stripSuffix("$")
  }
}
