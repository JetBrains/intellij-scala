package org.jetbrains.plugins.scala.tasty.reader

import Node.{Node1, Node2, Node3}
import TreePrinter.Keywords

import dotty.tools.tasty.TastyBuffer.Addr
import dotty.tools.tasty.TastyFormat.*
import org.jetbrains.plugins.scala.util.CommonQualifiedNames

import java.lang.Double.longBitsToDouble
import java.lang.Float.intBitsToFloat
import scala.annotation.{switch, tailrec}
import scala.collection.mutable

// TODO
// refactor
// use StringBuilder: type
// nonEmpty predicate
// implicit StringBuilder?
// indent: opaque type, implicit
class TreePrinter(privateMembers: Boolean = false, infixTypes: Boolean = false, legacySyntax: Boolean = false) {
  private final val Indent = "  "
  private final val CompiledCode = "???"

  // TODO use parameters
  private val sharedTypes = mutable.Map[Addr, String]()
  private val sourceFiles = mutable.Buffer[String]()

  // The use of SYNTHETIC, GIVEN, and IMPLICIT modifiers in `given` and `implicit class` differs in 3.0.0+

  private def isGivenObject0(typeDef: Node): Boolean = {
    def isGivenModule(node: Node) = node.is(VALDEF) && node.contains(OBJECT) && node.contains(GIVEN) && node.name + "$" == typeDef.name
    typeDef.contains(OBJECT) && typeDef.prevSibling.exists(isGivenModule)
  }

  private def isGivenClass0(typeDef: Node): Boolean = {
    def isGivenConversion(node: Node) = node.is(DEFDEF) && node.contains(GIVEN) && node.name == typeDef.name
    typeDef.contains(SYNTHETIC) && typeDef.nextSibling.exists(it => isGivenConversion(it) || it.nextSibling.exists(_.nextSibling.exists(isGivenConversion)))
  }

  private def isGivenConversion(defDef: Node) = {
    def isGivenClass(node: Node) = node.is(TYPEDEF) && node.contains(SYNTHETIC) && node.name == defDef.name
    defDef.contains(GIVEN) && defDef.prevSibling.exists(it => isGivenClass(it) || it.prevSibling.exists(_.prevSibling.exists(isGivenClass)))
  }

  private def isImplicitClass0(typeDef: Node): Boolean = {
    def isImplicitConversion(node: Node) = node.is(DEFDEF) && node.contains(SYNTHETIC) && node.contains(IMPLICIT) && node.name == typeDef.name
    typeDef.nextSibling.exists(isImplicitConversion)
  }

  private def isImplicitConversion(defDef: Node): Boolean = {
    def isImplicitClass(node: Node) = node.is(TYPEDEF) && node.contains(IMPLICIT) && node.name == defDef.name
    defDef.contains(IMPLICIT) && defDef.prevSibling.exists(isImplicitClass)
  }

  private def isPseudoPrivateTypeAlias(typeDef: Node): Boolean = !typeDef.firstChild.is(TEMPLATE) && {
    val repr = if (typeDef.firstChild.is(LAMBDAtpt)) typeDef.firstChild else typeDef
    !repr.children.exists(_.is(TYPEBOUNDStpt)) && {
      repr.children.find(_.isTypeTree) match {
        case Some(n) =>
          if (n.is(IDENTtpt)) !n.firstChild.is(TYPEREFsymbol) || !n.firstChild.refPrivate
          else if (n.is(APPLIEDtpt) && n.firstChild.is(IDENTtpt)) !n.firstChild.firstChild.is(TYPEREFsymbol) || !n.firstChild.firstChild.refPrivate
          else true
        case _ =>
          true
      }
    }
  }

  private def isPseudoPrivateObject(typeDef: Node): Boolean = typeDef.contains(OBJECT) &&
    (typeDef.prevSibling.exists(_.prevSibling.exists(n => n.is(TYPEDEF) && !n.contains(PRIVATE) && n.name == typeDef.name.stripSuffix("$"))) || typeDef.firstChild.children.exists {
      case n @ Node3(TYPEDEF, _, Seq(head, _ : _*)) if !head.is(TEMPLATE) && !n.contains(SYNTHETIC) => !n.contains(PRIVATE) || isPseudoPrivateTypeAlias(n)
      case _ => false
    })

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
          if (name != "<empty>" && (!containsPackageObject || name.contains('.'))) {
            sb ++= "package "
            val parts = name.split('.').map(id)
            if (containsPackageObject) {
              sb ++= parts.init.mkString(".")
            } else {
              sb ++= parts.mkString(".")
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
              children match {
                case Seq(Node2(VALDEF, Seq(name1)), cobj @ Node3(TYPEDEF, Seq(name2), _), ctpe @ Node3(TYPEDEF, Seq(name3), _)) if name2 == name1 + "$" && name3 == name1 =>
                  textOfMember(sb, indent, ctpe, if (containsPackageObject) Some(node) else None, "")
                  textOfMember(sb, indent, cobj, if (containsPackageObject) Some(node) else None, "\n\n")
                case _ =>
                  children.foreach { child =>
                    val previousLength = sb.length
                    textOfMember(sb, indent, child, if (containsPackageObject) Some(node) else None, if (delimiterRequired) "\n\n" else "")
                    delimiterRequired = delimiterRequired || sb.length > previousLength
                  }
              }
          }
      }

    case _ =>
      textOfMember(sb, indent, node, definition, prefix)
  }

  private def textOfMember(sb: StringBuilder, indent: String, node: Node, definition: Option[Node] = None, prefix: String = ""): Unit = node match {
    case node @ Node1(TYPEDEF) if (privateMembers || !node.contains(PRIVATE) || isPseudoPrivateTypeAlias(node) || isPseudoPrivateObject(node)) && (!node.contains(SYNTHETIC) || isGivenClass0(node)) => // TODO why both are synthetic?
      sb ++= prefix
      textOfTypeDef(sb, indent, node, definition)

    case node @ Node2(DEFDEF, Seq(name)) if (privateMembers || !node.contains(PRIVATE)) && !node.contains(SYNTHETIC) && !node.contains(FIELDaccessor) && !node.contains(ARTIFACT) && !name.contains("$default$") && !isGivenConversion(node) && !isImplicitConversion(node) =>
      sb ++= prefix
      textOfDefDef(sb, indent: String, node)

    case node @ Node1(VALDEF) if (privateMembers || !node.contains(PRIVATE)) && !node.contains(SYNTHETIC) && !node.contains(OBJECT) && (!node.contains(CASE) || definition.exists(_.contains(ENUM))) && !node.name.startsWith("derived$") =>
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
    val isGivenClass = isGivenClass0(node)
    val isImplicitClass = isImplicitClass0(node)
    val isTypeMember = !template.is(TEMPLATE)
    val isAnonymousGiven = (isGivenObject || isGivenClass) && name.startsWith("given_") // TODO common method
    val isPackageObject = isObject && definition.exists(_.is(PACKAGE))
    readSourceFileAnnotationIn(node)
    textOfAnnotationIn(sb, indent, node, "\n")
    sb ++= indent
    modifiersIn(sb, if (isObject) node.prevSibling.getOrElse(node) else node,
      if (isGivenClass) Set(GIVEN) else (if (isEnum) Set(ABSTRACT, SEALED, CASE, FINAL) else (if (isTypeMember) Set.empty else Set(OPAQUE))), isParameter = false)
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
    } else if (isGivenClass) {
      sb ++= "given "
    } else {
      sb ++= "class "
    }
    if (!isAnonymousGiven) {
      if (isPackageObject) {
        sb ++= id(definition.get.children.headOption.flatMap(_.name.split('.').lastOption).getOrElse("")) // TODO check
      } else {
        if (isObject) {
          sb ++= id(node.prevSibling.fold(name)(_.name)) // TODO check type
        } else {
          sb ++= id(name)
        }
      }
    }
    if (!isTypeMember) {
      textOfTemplate(sb, indent, template, Some(node))
    } else {
      val repr = node.children.headOption.filter(_.is(LAMBDAtpt)).getOrElse(node) // TODO handle LAMBDAtpt in parametersIn?
      val bounds = repr.children.find(_.is(TYPEBOUNDStpt))
      parametersIn(sb, repr, Some(repr))
      repr.children.foreach {
        case Node3(MATCHtpt, _, Seq(upperBound, tpe, _*)) if !tpe.is(CASEDEF) =>
          sb ++= " <: "
          sb ++= simple(textOfType(upperBound))
        case _ =>
      }
      if (bounds.isDefined) {
        boundsIn(sb, bounds.get)
      } else {
        if (!node.contains(OPAQUE)) { // TODO Enable when opaque types are implemented, #SCL-21516
          sb ++= " = "
          if (node.contains(OPAQUE)) {
            sb ++= "\"" + CompiledCode + "\""
          } else {
            repr.children.findLast(_.isTypeTree).orElse(repr.children.find(_.is(TYPEBOUNDS)).flatMap(_.children.headOption)) match {
              case Some(t) =>
                sb ++= simple(textOfType(t))
              case None =>
                sb ++= simple("") // TODO implement
            }
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
    def textOf(tpe: Node): String = textOfType(tpe, parens = 1)
    // TODO recursive textOf method, common syntactic sugar for FunctionN and TupleN
    val parents = children.collect { // TODO rely on name kind
      case node if node.isTypeTree => textOf(node)
      case Node3(APPLY, _, Seq(Node3(SELECTin, _, Seq(Node3(NEW, _, Seq(tpe, _: _*)), _: _*)), _: _*)) => textOf(tpe)
      case Node3(APPLY, _, Seq(Node3(APPLY, _, Seq(Node3(SELECTin, _, Seq(Node3(NEW, _, Seq(tpe, _: _*)), _: _*)), _: _*)), _: _*)) => textOf(tpe)
      case Node3(APPLY, _, Seq(Node3(TYPEAPPLY, _, Seq(Node3(SELECTin, _, Seq(Node3(NEW, _, Seq(base @ Node1(IDENTtpt), _: _*)), _: _*)), arguments: _*)), _: _*)) =>
        base.name + arguments.map(t => simple(textOfType(t))).mkString("[", ", ", "]")
      case Node3(APPLY, _, Seq(Node3(TYPEAPPLY, _, Seq(Node3(SELECTin, _, Seq(Node3(NEW, _, Seq(tpe, _: _*)), _: _*)), _: _*)), _: _*)) => textOf(tpe)
      case Node3(APPLY, _, Seq(Node3(APPLY, _, Seq(Node3(TYPEAPPLY, _, Seq(Node3(SELECTin, _, Seq(Node3(NEW, _, Seq(tpe, _: _*)), _: _*)), _: _*)), _: _*)), _: _*)) => textOf(tpe)
    }.filter(s => s.nonEmpty && s != "_root_.java.lang.Object" && s != "_root_.scala.runtime.EnumValue" &&
      !(isInCaseClass && CommonQualifiedNames.isProductOrScalaSerializableCanonical(s)))
      .map(simple)
    val isInGiven = definition.exists(it => isGivenObject0(it) || isGivenClass0(it))
    val isInAnonymousGiven = isInGiven && definition.exists(_.name.startsWith("given_")) // TODO common method

    val previousLength = sb.length
    primaryConstructor.foreach { constructor =>
      val sb1 = new StringBuilder() // TODO reuse
      val hasParameters = node.children.exists(_.is(PARAM))
      val hasModifiers = constructor.contains(PRIVATE) || constructor.contains(PROTECTED) || constructor.contains(PRIVATEqualified) || constructor.contains(PROTECTEDqualified)
      textOfAnnotationIn(sb1, "", constructor, " ", parens = hasParameters && !hasModifiers)
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
      // TODO enum Enum[+A] { case Case extends Enum[Nothing] }
      // TODO enum Enum[-A] { case Case extends Enum[Any] }
      if (parents.nonEmpty && !(parents.length == 1 && !parents.head.endsWith("]") && (definition.isEmpty || definition.exists(it => it.contains(ENUM) && it.contains(CASE))))) {
        sb ++= " extends " + parents.mkString(if (legacySyntax) " with " else ", ")
      }
      val derived = definition match {
        case Some(node) => node.nextSiblings.take(2).toSeq match {
          case Seq(Node2(VALDEF, Seq(name1)), cobj @ Node3(TYPEDEF, Seq(name2), _)) if name2 == name1 + "$" && node.name == name1 => cobj.firstChild.children.collect {
            case Node3(VALDEF, Seq(name), Seq(Node3(APPLIEDtpt | APPLIEDtype, _, Seq(tc, _: _*)), _: _*)) if name.startsWith("derived$") => textOfType(tc)
          }
          case _ => Seq.empty
        }
        case _ => Seq.empty
      }
      if (derived.nonEmpty) {
        sb ++= " derives " + derived.mkString(", ")
      }
    }
    val selfType = children.find(_.is(SELFDEF)) match {
      // Is there a more reliable way to determine whether self type refers to the same type definition?
      case Some(Node3(SELFDEF, Seq(name), Seq(tail))) if !definition.exists(_.contains(OBJECT)) &&
        definition.forall(it => !tail.refName.contains(it.name) && !tail.children.headOption.exists(_.refName.contains(it.name))) =>
        val isWith = (tail.is(APPLIEDtpt) || tail.is(APPLIEDtype)) && !tail.firstChild.is(IDENTtpt) && textOfType(tail.firstChild) == "_root_.scala.&"
        " " + (if (name == "_") "this" else name) + ": " + simple(textOfType(tail, parens = if (isWith) 0 else 1)) + " =>"
      case _ => ""
    }
    val members = {
      val cases = // TODO check element types
        if (isInEnum) definition.get.nextSibling.get.nextSibling.get.children.head.children.filter(it => it.is(VALDEF) || it.is(TYPEDEF))
        else Seq.empty

      children.filter(it => it.is(DEFDEF, VALDEF, TYPEDEF) && !primaryConstructor.contains(it)) ++ cases // TODO type member
    }
    if (selfType.nonEmpty || members.nonEmpty) {
      sb ++= " {"
      sb ++= selfType
      sb ++= "\n"
      val previousLength = sb.length
      var delimiterRequired = false
      members.foreach { member =>
        val previousLength = sb.length
        textOfMember(sb, indent + Indent, member, definition, if (delimiterRequired) "\n\n" else "")
        delimiterRequired = delimiterRequired || sb.length > previousLength
      }
      if (selfType.nonEmpty || sb.length > previousLength) {
        if (sb.length > previousLength) {
          sb ++= "\n"
        }
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
      parametersIn(sb, node, target = Target.This)
      sb ++= " = "
      sb ++= CompiledCode
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
      modifiersIn(sb, node, (if (isAbstractGiven) Set(GIVEN, FINAL) else Set.empty), isParameter = false)
      if (isAbstractGiven) {
        sb ++= "given "
      } else {
        sb ++= (if (node.contains(STABLE)) "val " else "def ")
      }
      val isAnonymousGiven = isAbstractGiven && name.startsWith("given_")
      var nameId = ""
      if (!isAnonymousGiven) {
        nameId = id(name)
        sb ++= nameId
      }
      val remainder = node.children.dropWhile(_.is(TYPEPARAM, PARAM, EMPTYCLAUSE, SPLITCLAUSE))
      val resultType = simple(remainder.headOption.map(textOfType(_)).getOrElse("")) // TODO implement
      val previousLength = sb.length
      parametersIn(sb, node, target = if (node.contains(EXTENSION)) Target.ExtensionMethod else Target.Definition, resultType = Some(resultType))
      if (sb.length == previousLength && needsSpace(nameId)) {
        sb ++= " "
      }
      sb ++= ": "
      sb ++= resultType
      val isDeclaration = remainder.drop(1).forall(_.isModifier)
      if (!isDeclaration) {
        sb ++= " = "
        sb ++= CompiledCode
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
      sb ++= id(name)
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
      val tpe = children.headOption
      tpe match {
        case Some(const @ Node1(UNITconst | TRUEconst | FALSEconst | BYTEconst | SHORTconst | INTconst | LONGconst | FLOATconst | DOUBLEconst | CHARconst | STRINGconst | NULLconst)) =>
          val nameId = id(name)
          sb ++= nameId
          sb ++= " = "
          sb ++= textOfConstant(const)
        case _ =>
          val isAnonymousGiven = isGivenAlias && name.startsWith("given_") // TODO How to detect anonymous givens reliably?
          if (!isAnonymousGiven) {
            val nameId = id(name)
            sb ++= nameId
            if (needsSpace(nameId)) {
              sb ++= " "
            }
            sb ++= ": "
          }
          tpe match {
            case Some(t) =>
              sb ++= simple(textOfType(t))
            case None =>
              sb ++= simple("") // TODO implement
          }
          val isDeclaration = children.drop(1).forall(_.isModifier)
          if (!isDeclaration) {
            sb ++= " = "
            sb ++= CompiledCode
          }
      }
    }
  }

  private def simple(tpe: String): String =
    if (tpe.nonEmpty) tpe else "Unknown" // TODO Remove when all types are supported

  // TODO include in textOfType
  // TODO keep prefixes? but those are not "relative" imports, but regular (implicit) imports of each Scala compilation unit
  private def simple0(tpe: String): String = {
    val s4 = {
      if (tpe.contains("this.")) tpe.substring(tpe.indexOf("this.") + (if (tpe.endsWith("this.type")) 0 else 5)) else {
        val s1 = tpe.stripPrefix("_root_.")
        val s2 = if (!s1.stripPrefix("scala.").takeWhile(!_.isWhitespace).stripSuffix(".type").contains('.')) s1.stripPrefix("scala.") else s1
        val s3 = if (!s2.stripPrefix("java.lang.").takeWhile(!_.isWhitespace).stripSuffix(".type").contains('.')) s2.stripPrefix("java.lang.") else s2
        if (!s3.stripPrefix("scala.Predef.").takeWhile(!_.isWhitespace).stripSuffix(".type").contains('.')) s3.stripPrefix("scala.Predef.") else s3
      }
    }
    if (s4.nonEmpty) s4 else "Unknown" // TODO Remove when all types are supported
  }

  private def textOfType(node: Node, parens: Int = 0)(using parent: Option[Node] = None): String = {
    if (node.isSharedType) {
      sharedTypes.get(node.addr) match {
        case Some(text) =>
          return text + (if (parent.isEmpty && node.is(TERMREF) && !text.endsWith(".type")) ".type" else "")
        case _ =>
      }
    }
    // TODO extract method
    given Option[Node] = Some(node)
    val text = node match { // TODO proper settings
      case Node3(IDENTtpt, _, Seq(tail)) => textOfType(tail)
      case Node3(SINGLETONtpt, _, Seq(tail)) =>
        val literal = textOfConstant(tail)
        if (literal.nonEmpty) literal else textOfType(tail) + (if (tail.is(TERMREF)) "" else ".type")
      case Node3(TYPEREF, Seq(name), Seq(tail)) => textOfType(tail) + "." + id(name)
      case Node3(TERMREF, Seq(name), Seq(tail)) => if (name == "package" || name.endsWith("$package")) textOfType(tail) else textOfType(tail) + "." + id(name) + // TODO why there's "package" in some cases?
          (if (parent.forall(_.is(SINGLETONtpt))) ".type" else "") // TODO Why there is sometimes no SINGLETONtpt? (add RHS?)
      case Node3(THIS, _, Seq(tail)) =>
        val qualifier = textOfType(tail)
        if (qualifier.endsWith("package$")) { val i = qualifier.lastIndexOf('.'); qualifier.substring(0, if (i == -1) qualifier.length - 8 else i) }
        else if (qualifier.endsWith("$")) qualifier.substring(0, qualifier.length - 1) // What is the semantics of "this" when referring to external module classes?
        else if (qualifier == "_root_.`<empty>`") "" else qualifier.split('.').last + ".this"
      case Node3(QUALTHIS, _, Seq(tail)) =>
        val qualifier = textOfType(tail)
        qualifier.split('.').last + ".this" // Simplify Foo.this in Foo?
      case Node3(TYPEREFsymbol | TYPEREFdirect | TERMREFsymbol | TERMREFdirect, _, tail) =>
        val prefix = if (node.refTag.contains(TYPEPARAM)) "" else tail.headOption.map(textOfType(_)).getOrElse("")
        val name = node.refName.getOrElse("")
        if (name == "package" || name.endsWith("$package")) prefix
        else (if (prefix.isEmpty) id(name) else prefix + "." + id(name)) + (if (parent.isEmpty && node.is(TERMREFsymbol, TERMREFdirect)) ".type" else "") // TODO rely on name kind
      case Node3(SELECTtpt | SELECT, Seq(name), Seq(tail)) =>
        val selector = if (node.tag == SELECTtpt && node.children.headOption.exists(it => isTypeTreeTag(it.tag))) "#" else "."
        val qualifier = textOfType(tail)
        val qualifierInParens = if (selector == "#" && tail.is(REFINEDtpt)) "(" + qualifier + ")" else qualifier
        if (qualifier.nonEmpty) qualifierInParens + selector + id(name) else id(name)
      case Node2(TERMREFpkg | TYPEREFpkg, Seq(name)) => if (name == "_root_") name else "_root_." + name.split('.').map(id).mkString(".")
      case Node3(APPLIEDtpt | APPLIEDtype, _, Seq(constructor, arguments: _*)) =>
        val base = textOfType(constructor)
        val simpleBase = if (infixTypes) simple0(base) else base
        val isInfix = infixTypes && simpleBase.forall(!_.isLetterOrDigit) && arguments.length == 2
        val isWith = (legacySyntax || !constructor.is(IDENTtpt)) && base == "_root_.scala.&"
        if (isInfix || isWith) {
          val s = arguments.map(it => simple(textOfType(it, parens = if (isWith) 0 else 1))).mkString(" " + (if (isWith) "with" else simpleBase) + " ")
          if (parens > 0) "(" + s + ")" else s
        } else if (base == "_root_.scala.`<repeated>`") {
          textOfType(arguments.head, parens = 1) + "*" // TODO why repeated parameters in aliases are encoded differently?
        } else if (base.startsWith("_root_.scala.Tuple") && base != "_root_.scala.Tuple1" && !base.substring(18).contains(".")) { // TODO use regex
          val s = arguments.map(it => simple(textOfType(it))).mkString("(", ", ", ")")
          if (parens > 1) "(" + s + ")" else s
        } else if (base.startsWith("_root_.scala.Function") || base.startsWith("_root_.scala.ContextFunction")) {
          val arrow = if (base.startsWith("_root_.scala.Function")) " => " else " ?=> "
          val s = (if (arguments.length == 2) simple(textOfType(arguments.head, parens = 2)) else arguments.init.map(it => simple(textOfType(it))).mkString("(", ", ", ")")) + arrow + simple(textOfType(arguments.last))
          if (parens > 0) "(" + s + ")" else s
        } else {
          simpleBase + "[" + arguments.map(it => simple(textOfType(it))).mkString(", ") + "]"
        }
      case Node3(ANDtype | ORtype, _, Seq(l, r)) =>
        if (infixTypes) {
          val s = simple(textOfType(l)) + (if (node.is(ANDtype)) " & " else " | ") + simple(textOfType(r))
          if (parens > 0) "(" + s + ")" else s
        } else {
          "_root_.scala." + (if (node.is(ANDtype)) "&" else "|") + "[" + simple(textOfType(l)) + ", " + simple(textOfType(r)) + "]"
        }
      case Node3(ANNOTATEDtpt | ANNOTATEDtype, _, Seq(tpe, annotation)) =>
        annotation match {
          case Node3(APPLY, _, Seq(Node3(SELECTin, _, Seq(Node3(NEW, _, Seq(tpe0, _: _*)), _: _*)), _: _*)) =>
            val s = textOfType(tpe0)
            if (s == "_root_.scala.annotation.internal.Repeated") textOfType(tpe.children(1), parens = 1) + "*"
            else if (s != "_root_.scala.annotation.internal.InlineParam") textOfType(tpe) // SCL-21207
            else textOfType(tpe) + " " + "@" + simple(s) + {
              val args = annotation.children.map(textOfConstantOrArray).filter(_.nonEmpty).mkString(", ")
              if (args.nonEmpty) "(" + args + ")" else ""
            }
          case _ => textOfType(tpe)
        }
      case Node3(BYNAMEtpt, _, Seq(tpe)) =>
        val s = "=> " + simple(textOfType(tpe))
        if (parens > 1) "(" + s + ")" else s

      case Node3(MATCHtpt, _, children) =>
        val (tpe, cases) = children match {
          case tpe :: (cases @ Seq(Node1(CASEDEF), _: _*)) => (tpe, cases)
          case _ :: tpe :: (cases @ Seq(Node1(CASEDEF), _: _*)) => (tpe, cases)
        }
        val cs = cases.map {
          case Node3(CASEDEF, _, Seq(t1, t2)) => "case " + simple(textOfType(t1)) + " => " + simple(textOfType(t2))
        }
        simple(textOfType(tpe)) + " match { " + cs.mkString(" ") + " }"

      case Node1(BIND) => if (node.name.startsWith("_$")) "_" else id(node.name)

      case Node1(TYPEBOUNDStpt | TYPEBOUNDS) =>
        val sb1 = new StringBuilder() // TODO reuse
        boundsIn(sb1, node)
        (if (legacySyntax) "_" else "?") + sb1.toString

      case Node3(LAMBDAtpt, _, children) =>
        val sb1 = new StringBuilder() // TODO reuse
        parametersIn(sb1, node, uniqueNames = true) // We might use the built-in kind projector syntactic sugar, but there's no way to known whether the code has been compiled with -Ykind-projector
        sb1.toString + " =>> " + children.lastOption.map(textOfType(_)).getOrElse("") // TODO check tree

      case Node3(TYPELAMBDAtype, _, Seq(Node3(APPLIEDtype, _, Seq(tail, _: _*)), _: _*)) => textOfType(tail)

      case Node3(REFINEDtpt, _, Seq(tr @ Node1(TYPEREF), Node3(DEFDEF, Seq(name), children), _ : _*)) if textOfType(tr) == "_root_.scala.PolyFunction" && name == "apply" => // TODO check tree
        val (typeParams, tail1) = children.span(_.is(TYPEPARAM))
        val (valueParams, tails2) = tail1.span(_.is(PARAM))
        val s = typeParams.map(tp => id(tp.name)).mkString("[", ", ", "]") + " => " + {
          val params = valueParams.flatMap(_.children.headOption.map(tpe => simple(textOfType(tpe)))).mkString(", ")
          if (valueParams.length == 1) params else "(" + params + ")"
        } + " => " + tails2.headOption.map(tpe => simple(textOfType(tpe))).getOrElse("")
        if (parens > 0) "(" + s + ")" else s
      case Node3(REFINEDtpt, _, Seq(tpe, members: _*)) =>
        val prefix = textOfType(tpe)
        (if (prefix == "_root_.scala.AnyRef" || prefix == "_root_.java.lang.Object") "" else simple(prefix) + " ") + "{ " + members.map(it => { val sb = new StringBuilder(); textOfMember(sb, "", it); sb.toString }).mkString("; ") + " }" // TODO use sb directly

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
    case LONGconst => s"${node.value}L"
    case FLOATconst => s"${intBitsToFloat(node.value.toInt)}F"
    case DOUBLEconst => s"${longBitsToDouble(node.value)}D"
    case CHARconst => "'" + escape(node.value.toChar.toString) + "'"
    case STRINGconst => "\"" + escape(node.name) + "\""
    case NULLconst => "null"
    case _ => ""
  }

  // TODO Complete
  private def escape(s: String): String =
    s.replace("\r", "\\r").replace("\n", "\\n")

  private def textOfArray(node: Node): String = node match {
    case Node3(APPLY, _, Seq(
           Node3(APPLY, _, Seq(
             Node3(TYPEAPPLY, _, Seq(
               Node3(SELECTin, Seq("apply[...]"), Seq(
                 Node2(TERMREF, Seq("Array")),
                 _)),
               _)),
             Node3(TYPED, _, Seq(
               Node3(REPEATED, _, args),
               _)))),
           _)) => "_root_.scala.Array(" + args.map(textOfConstantOrArray).filter(_.nonEmpty).mkString(", ") + ")"
    case _ => ""
  }

  private def textOfConstantOrArray(node: Node): String = textOfConstant(node) match {
    case "" => textOfArray(node)
    case s => s
  }

  private def textOfAnnotationIn(sb: StringBuilder, indent: String, node: Node, suffix: String, parens: Boolean = false): Unit = {
    node.children.reverseIterator.takeWhile(_.is(ANNOTATION)).foreach {  // TODO sb.insert?
      case Node3(ANNOTATION, _, Seq(tpe, apply @ Node3(APPLY, _, Seq(tail, _: _*)))) =>
        val name = Option(tpe).map(textOfType(_)).filter(!_.startsWith("_root_.scala.annotation.internal.")).map(simple).getOrElse("") // TODO optimize
        if (name.nonEmpty) {
          sb ++= indent
          sb ++= "@" + simple(name.split('.').map(id).mkString("."))
          tail match {
            case Node3(TYPEAPPLY, _, Seq(_, args: _*)) =>
              sb ++= "["
              sb ++= args.map(arg => simple(textOfType(arg))).mkString(", ")
              sb ++= "]"
            case _ =>
          }
          val args = apply.children.map(textOfConstantOrArray).filter(_.nonEmpty) // TODO optimize
          val namedArgs = apply.children.collect {
            case Node3(NAMEDARG, Seq(name), Seq(tail)) => name + " = " + textOfConstantOrArray(tail)
          }
          if (parens || args.nonEmpty || namedArgs.nonEmpty) {
            sb ++= "("
            sb ++= (args ++ namedArgs).mkString(", ")
            sb ++= ")"
          }
          sb ++= suffix
        }
      case _ =>
    }
  }

  private def readSourceFileAnnotationIn(node: Node): Unit = {
    node.children.reverseIterator.takeWhile(_.is(ANNOTATION)).foreach {
      case Node3(ANNOTATION, _, Seq(tpe, apply@Node1(APPLY))) if (textOfType(tpe) == "_root_.scala.annotation.internal.SourceFile") =>
        apply.children.lastOption.map(_.name).foreach { path =>
          val i = path.replace('\\', '/').lastIndexOf("/")
          sourceFiles += (if (i > 0) path.substring(i + 1) else path)
        }
      case _ =>
    }
  }

  private enum Target {
    case This
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

  private def parametersIn(sb: StringBuilder, node: Node, template: Option[Node] = None, definition: Option[Node] = None, target: Target = Target.Definition, modifiers: StringBuilder => Unit = _ => (), uniqueNames: Boolean = false, resultType: Option[String] = None): Unit = {
    val tps = target match {
      case Target.This => Seq.empty
      case Target.Definition => node.children
      case Target.Extension => node.children.takeWhile(!_.is(PARAM))
      case Target.ExtensionMethod => node.children.dropWhile(!_.is(PARAM))
    }

    val templateTypeParams = template.map(_.children.filter(_.is(TYPEPARAM)).iterator)

    val isPrivateConstructor = node.is(DEFDEF) && node.names == Seq("<init>") && node.contains(PRIVATE)

    lazy val contextBounds = if (!privateMembers && isPrivateConstructor) Seq.empty else node.children.collect {
      case param @ Node3(PARAM, Seq(name), Seq(tail, _: _*)) if name.startsWith("evidence$") && param.contains(IMPLICIT) && hasSingleArgument(tail) =>
        val Seq(designator, argument) = tail.children
        (simple(textOfType(argument)), simple(textOfType(designator)))
    }

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
        val nameId = if (!uniqueNames && name.startsWith("_$")) "_" else id(name)
        sb ++= nameId // TODO detect Unique name
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
        contextBounds.foreach { case (id, tpe) =>
          if (id == name) {
            if (needsSpace(nameId)) {
              sb ++= " "
            }
            sb ++= ": "
            sb ++= tpe
          }
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
      case Target.This | Target.Definition => node.children
      case Target.Extension =>
        popExtensionParams(mutable.Stack[Node](node.children: _*))
      case Target.ExtensionMethod =>
        val stack = mutable.Stack[Node](node.children: _*)
        popExtensionParams(stack)
        stack.toSeq.dropWhile(_.is(SPLITCLAUSE))
    }

    val templateValueParams = template.map(_.children.filter(_.is(PARAM)).iterator)

    open = false
    next = false

    var isFirstClause = true
    var isImplicitClause = false
    var isGivenClause = false

    val valueParameterStart = sb.length
    var syntheticParameterNames = List.empty[(String, Int)]

    ps.foreach {
      case Node1(EMPTYCLAUSE) =>
        if (open) {
          sb ++= ")"
          open = false
          isFirstClause = false
        }
        sb ++= "()"
      case Node1(SPLITCLAUSE) =>
        sb ++= ")"
        open = false
        isFirstClause = false
      case node @ Node3(PARAM, Seq(name), children) if !(name.startsWith("evidence$") && node.contains(IMPLICIT) && hasSingleArgument(children.head)) =>
        if (!open) {
          sb ++= "("
          open = true
          next = false
          if (node.contains(GIVEN)) {
            sb ++= "using "
            isGivenClause = true
          } else {
            if (node.contains(IMPLICIT)) {
              sb ++= "implicit "
              isImplicitClause = true
            }
          }
        }
        val templateValueParam = templateValueParams.map(_.next())
        if (privateMembers || !isPrivateConstructor || templateValueParam.exists(!_.contains(PRIVATE))) { // TODO private (), variables in { ... }
          if (next) {
            sb ++= ", "
          }
          textOfAnnotationIn(sb, "", node, " ")
          val tpe = textOfType(children.head)
          if (node.contains(INLINE) || tpe.endsWith(" @_root_.scala.annotation.internal.InlineParam")) {
            sb ++= "inline "
          }
          if (!definition.exists(isGivenClass0)) {
            templateValueParam.foreach { valueParam =>
              if (!valueParam.contains(LOCAL)) {
                textOfAnnotationIn(sb, "", valueParam, " ")
                val sb1 = new StringBuilder() // TODO reuse
                val isPrivate = valueParam.contains(PRIVATE)
                modifiersIn(sb1, valueParam, (if (isImplicitClause) Set(IMPLICIT) else if (isGivenClause) Set(GIVEN) else Set.empty) ++ (if (privateMembers || !isPrivate) Set.empty else Set(ABSTRACT, OVERRIDE, PRIVATE, IMPLICIT, FINAL)))
                sb ++= sb1
                if (privateMembers || !isPrivate) {
                  if (valueParam.contains(MUTABLE)) {
                    sb ++= "var "
                  } else {
                    if (!(isFirstClause && definition.exists(_.contains(CASE)) && valueParam.modifierTags.forall(it => it == CASEaccessor || it == HASDEFAULT))) {
                      sb ++= "val "
                    }
                  }
                }
              }
            }
          }
          val isSyntheticParam = node.contains(SYNTHETIC) || templateValueParam.exists(_.contains(SYNTHETIC))
          if (!isSyntheticParam) {
            val nameId = id(name)
            sb ++= nameId
            if (needsSpace(nameId)) {
              sb ++= " "
            }
            sb ++= ": "
          } else {
            syntheticParameterNames ::= (name, sb.length)
          }
          sb ++= simple(tpe).stripSuffix(" @_root_.scala.annotation.internal.InlineParam")
          if (node.contains(HASDEFAULT)) {
            sb ++= " = "
            sb ++= CompiledCode
          }
          next = true
        }
      case _ =>
    }
    if (open) {
      if (!next) {
        if (isImplicitClause) {
          sb.setLength(sb.length - 9)
        } else if (isGivenClause) {
          sb.setLength(sb.length - 6)
        }
      }
      sb ++= ")"
    }
    val valueParameterText = sb.substring(valueParameterStart) // TODO Check nodes rather than text
    syntheticParameterNames.foreach { (name, index) =>
      //example from `scala.annotation.MacroAnnotation#transform`
      //original code : def transform(using Quotes)(tree: quotes.reflect.Definition): List[quotes.reflect.Definition]
      //printed code  : def transform(using _root_.scala.quoted.Quotes)(tree: x$1.reflect.Definition): List[x$1.reflect.Definition]
      val syntheticParameterIsLikelyUsedInSignature =
        valueParameterText.contains(name) || resultType.exists(_.contains(name))
      val insertSyntheticParameterName = syntheticParameterIsLikelyUsedInSignature
      if (insertSyntheticParameterName) {
        val nameId = id(name)
        val paramNamePrefix = name + (if (needsSpace(nameId)) " " else "") + ": "
        sb.insert(index, paramNamePrefix)
      }
    }
    if (template.isEmpty || hasModifiers || definition.exists(it => it.contains(CASE) && !it.contains(OBJECT))) {} else {
      if (sb.length >= 2 && sb.substring(sb.length - 2, sb.length()) == "()" && !(sb.length > 2 && sb.charAt(sb.length - 3) == ')')) {
        sb.delete(sb.length - 2, sb.length())
      }
    }
  }

  private def hasSingleArgument(tpe: Node): Boolean = tpe match {
    case Node3(APPLIEDtpt | APPLIEDtype, _, Seq(_, _)) => true
    case _ => false
  }

  private def modifiersIn(sb: StringBuilder, node: Node, excluding: Set[Int] = Set.empty, isParameter: Boolean = true): Unit = { // TODO Optimize
    if (node.contains(ABSTRACT) && !excluding(ABSTRACT) && node.contains(OVERRIDE)) {
      sb ++= "abstract override "
    } else {
      if (node.contains(OVERRIDE) && !excluding(OVERRIDE)) {
        sb ++= "override "
      }
    }
    if (node.contains(PRIVATE) && !excluding(PRIVATE)) {
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
    if (node.contains(IMPLICIT) && !excluding(IMPLICIT)) {
      sb ++= "implicit "
    }
    if (node.contains(FINAL) && !excluding(FINAL)) {
      sb ++= "final "
    }
    if (node.contains(LAZY) && !excluding(LAZY)) {
      sb ++= "lazy "
    }
    if (node.contains(ABSTRACT) && !excluding(ABSTRACT) && !node.contains(OVERRIDE)) {
      sb ++= "abstract "
    }
    if (node.contains(TRANSPARENT)) {
      sb ++= "transparent "
    }
    if (node.contains(OPAQUE) && !excluding(OPAQUE)) {
      //sb ++= "opaque " // TODO Enable when opaque types are implemented, #SCL-21516
    }
    if (node.contains(INLINE)) {
      sb ++= "inline "
    }
    if (node.contains(CASE) && !excluding(CASE)) {
      sb ++= "case "
    }
  }

  private def boundsIn(sb: StringBuilder, node: Node): Unit = node match {
    case Node3(TYPEBOUNDStpt | TYPEBOUNDS, _, Seq(lower, upper)) =>
      val l = textOfType(lower)
      if (l.nonEmpty && l != "_root_.scala.Nothing") {
        sb ++= " >: " + simple(l)
      }
      val u = textOfType(upper)
      if (u.nonEmpty && u != "_root_.scala.Any") {
        sb ++= " <: " + simple(u)
      }
    case _ => // TODO exhaustive match
  }

  private def asQualifier(tpe: String): String = {
    val i = tpe.lastIndexOf(".")
    (if (i == -1) tpe else tpe.drop(i + 1)).stripSuffix("$")
  }

  private def id(s: String): String =
    if (Keywords(s) || !isIdentifier(s)) "`" + s + "`" else s

  private def isIdentifier(s: String): Boolean = !(s.isEmpty || s.contains("//") || s.contains("/*")) && {
    if (s(0) == '_' || s(0) == '$' || Character.isUnicodeIdentifierStart(s(0))) {
      val lastIdCharIdx = s.takeWhile(c => c == '$' || Character.isUnicodeIdentifierPart(c)).length - 1
      if (lastIdCharIdx < 0 || lastIdCharIdx == s.length - 1) true
      else if (s.charAt(lastIdCharIdx) != '_') false
      else s.drop(lastIdCharIdx + 1).forall(isOperatorPart)
    } else if (isOperatorPart(s(0))) {
      s.forall(isOperatorPart)
    } else {
      false
    }
  }

  //TODO: this duplicates org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.isOpCharacter
  // extract it to some common utility in a module accessible to both modules (e.g. scala-utils-language)
  private def isOperatorPart(c: Char): Boolean = (c: @switch) match {
    case '~' | '!' | '@' | '#' | '%' | '^' | '*' | '+' | '-' | '<' | '>' | '?' | ':' | '=' | '&' | '|' | '/' | '\\' => true
    case c => val ct = Character.getType(c); ct == Character.MATH_SYMBOL.toInt || ct == Character.OTHER_SYMBOL.toInt
  }

  //For example `???` requires extra space after it: `??? : String`
  private def needsSpace(id: String) = id.lastOption.exists(c => !c.isLetterOrDigit && c != '`')
}

private object TreePrinter {
  private val Keywords = Set(
    "=",
    "=>",
    "=>>",
    "?=>",
    "abstract",
    "case",
    "catch",
    "class",
    "def",
    "do",
    "else",
    "enum",
    "extends",
    "extension",
    "false",
    "final",
    "finally",
    "for",
    "forSome",
    "given",
    "if",
    "implicit",
    "import",
    "lazy",
    "macro",
    "match",
    "new",
    "null",
    "object",
    "override",
    "package",
    "private",
    "protected",
    "return",
    "sealed",
    "super",
    "then",
    "this",
    "throw",
    "trait",
    "true",
    "try",
    "type",
    "val",
    "var",
    "while",
    "with",
    "yield",
  )
}