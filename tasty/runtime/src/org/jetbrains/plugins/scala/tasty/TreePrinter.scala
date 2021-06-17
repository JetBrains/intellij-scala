package org.jetbrains.plugins.scala.tasty

import dotty.tools.tasty.TastyFormat._

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

  def textOf(node: Node, definition: Option[Node] = None): String = node match {
    case Node(PACKAGE, _, Seq(Node(TERMREFpkg, Seq(name), _), tail: _*)) => tail match {
      case Seq(node @ Node(PACKAGE, _, _), _: _*) => textOf(node) // TODO make sure that there's a single nested package
      case _ =>
        (if (name == "<empty>") "" else ("package " + name + "\n\n")) + (tail match {
          case Seq(Node(VALDEF, Seq(name1), _: _*), Node(TYPEDEF, Seq(name2), Seq(template, _: _*)), _: _*) if name1.endsWith("$package") && name2.endsWith("$package$") =>
            template.children.filter(it => it.is(DEFDEF, VALDEF, TYPEDEF) && it.names != Seq("<init>")).map(textOf(_)).filter(_.nonEmpty).mkString("\n\n")
          case _ =>
            tail.map(textOf(_)).filter(_.nonEmpty).mkString("\n\n")
        })
    }

    case node @ Node(TYPEDEF, Seq(name), Seq(template, _: _*)) if !node.hasFlag(SYNTHETIC) || isGivenImplicitClass0(node) => // TODO why both are synthetic?
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
      modifiers + keyword + (if (isAnonymousGiven) "" else identifier) + (if (!isTypeMember) textOf(template, Some(node)) else {
        val repr = node.children.headOption.filter(_.is(LAMBDAtpt)).getOrElse(node)
        val bounds = repr.children.find(_.is(TYPEBOUNDStpt))
        parametersIn(repr, Some(repr)) + (if (bounds.isDefined) boundsIn(bounds.get)
        else " = " + (if (node.hasFlag(OPAQUE)) "???" else textOf(repr.children.findLast(_.isTypeTree).get))) // TODO parameter, { /* compiled code */ }
      })

    case node @ Node(TEMPLATE, _, children) => // TODO method?
      val primaryConstructor = children.find(it => it.is(DEFDEF) && it.names == Seq("<init>"))
      val modifiers = primaryConstructor.map(modifiersIn(_)).map(it => if (it.nonEmpty) " " + it else "").getOrElse("")
      val parameters = primaryConstructor.map(it => parametersIn(it, Some(node), definition)).getOrElse("")
      val parents0 = children.collect {
        case node if node.isTypeTree => Some(textOf(node))
        case Node(APPLY, _, Seq(Node(SELECTin, _, Seq(Node(NEW, _, Seq(tpe, _: _*)), _: _*)), args: _*)) =>
          Some(textOf(tpe)).filter(_ != "Object") // TODO FQN
        case Node(APPLY, _, Seq(Node(APPLY, _, Seq(Node(SELECTin, _, Seq(Node(NEW, _, Seq(tpe, _: _*)), _: _*)), _: _*)), args: _*)) =>
          Some(textOf(tpe)).filter(_ != "Object") // TODO FQN
        case Node(APPLY, _, Seq(Node(TYPEAPPLY, _, Seq(Node(SELECTin, _, Seq(Node(NEW, _, Seq(tpe, _: _*)), _: _*)), _: _*)), args: _*)) =>
          Some(textOf(tpe)).filter(_ != "Object") // TODO FQN
      }
      val parents = parents0.collect{ case Some(s) if s.nonEmpty => s }
      val isInEnum = definition.exists(_.hasFlag(ENUM))
      val isInGiven = definition.exists(it => isGivenObject0(it) || isGivenImplicitClass0(it))
      val isInAnonymousGiven = isInGiven && definition.exists(_.name.startsWith("given_")) // TODO common method
      val cases = // TODO check element types
        if (isInEnum) definition.get.nextSibling.get.nextSibling.get.children.head.children.filter(it => it.is(VALDEF) || it.is(TYPEDEF))
        else Seq.empty
      val members = (children.filter(it => it.is(DEFDEF, VALDEF, TYPEDEF) && !primaryConstructor.contains(it)) ++ cases) // TODO type member
        .map(textOf(_, definition)).filter(_.nonEmpty).map(indent).mkString("\n\n")
      (modifiers + (if (modifiers.nonEmpty && parameters.isEmpty) "()" else parameters)) +
        (if (isInGiven && (!isInAnonymousGiven || parameters.nonEmpty)) ": " else "") +
        (if (isInGiven) (parents.mkString(" with ") + " with") else (if (parents.isEmpty) "" else " extends " + parents.mkString(" with "))) +
        (if (members.isEmpty) (if (isInGiven) " {}" else "") else " {\n" + members + "\n}")

    case node @ Node(DEFDEF, Seq(name), children) if !node.hasFlag(FIELDaccessor) && !node.hasFlag(SYNTHETIC) && !name.contains("$default$") => // TODO why it's not synthetic?
      val isAbstractGiven = node.hasFlag(GIVEN)
      val isDeclaration = children.filter(!_.isModifier).lastOption.exists(_.isTypeTree)
      val tpe = children.find(_.isTypeTree)
      children.filter(_.is(EMPTYCLAUSE, PARAM))
      if (name == "<init>") {
        modifiersIn(node) + "def this" + parametersIn(node) + " = ???" // TODO parameter, { /* compiled code */ }
      } else {
        (if (node.hasFlag(EXTENSION)) "extension " + parametersIn(node, target = Target.Extension) + "\n  " else "") +
          modifiersIn(node, (if (isAbstractGiven) Set(FINAL) else Set.empty), isParameter = false) + (if (isAbstractGiven) "" else "def ") + name + parametersIn(node, target = if (node.hasFlag(EXTENSION)) Target.ExtensionMethod else Target.Definition) +
          ": " + tpe.map(textOf(_)).getOrElse("") + (if (isDeclaration) "" else " = ???") // TODO parameter, { /* compiled code */ }
      }

    case node @ Node(VALDEF, Seq(name), children) if !node.hasFlag(SYNTHETIC) && !node.hasFlag(OBJECT) =>
      val isDeclaration = children.filter(!_.isModifier).lastOption.exists(_.isTypeTree)
      val isCase = node.hasFlag(CASE)
      val isGivenAlias = node.hasFlag(GIVEN)
      val isAnonymousGiven = isGivenAlias && name.startsWith("given_") // TODO How to detect anonymous givens reliably?
      val tpe = children.find(_.isTypeTree)
      val template = // TODO check element types
        if (isCase) children.lift(1).flatMap(_.children.lift(1)).flatMap(_.children.headOption).map(textOf(_)).getOrElse("")
        else ""
      if (isCase && !definition.exists(_.hasFlag(ENUM))) "" else modifiersIn(node, (if (isGivenAlias) Set(FINAL, LAZY) else Set.empty), isParameter = false) + (if (isCase) name +  template else
        (if (isGivenAlias) "" else (if (node.hasFlag(MUTABLE)) "var " else "val ")) +
          (if (isAnonymousGiven) "" else name + ": ") +
          tpe.map(textOf(_)).getOrElse("") + (if (isDeclaration) "" else " = ???")) // TODO parameter, /* compiled code */

    // TODO method?
    case Node(IDENTtpt, Seq(name), _) => name
    case Node(TYPEREF, Seq(name), _) => name
    case Node(APPLIEDtpt, _, Seq(constructor, arguments: _*)) => textOf(constructor) + "[" + arguments.map(textOf(_)).mkString(", ") + "]"
    case Node(ANNOTATEDtpt | ANNOTATEDtype, _, Seq(tpe, annotation)) =>
      annotation match {
        case Node(APPLY, _, Seq(Node(SELECTin, _, Seq(Node(NEW, _, Seq(tpe0, _: _*)), _: _*)), args: _*)) if textOf(tpe0) == "Repeated" => // TODO FQN
          textOf(tpe.children(1)) + "*" // TODO check tree (APPLIEDtpt)
        case _ => textOf(tpe)
      }
    case Node(BYNAMEtpt, _, Seq(tpe)) => "=> " + textOf(tpe)

    case _ => ""
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

  private def parametersIn(node: Node, template: Option[Node] = None, definition: Option[Node] = None, target: Target = Target.Definition): String = {
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
      case Node(TYPEPARAM, Seq(name), Seq(bounds @ Node(TYPEBOUNDStpt, _, _: _*), _: _*)) =>
        if (!open) {
          params += "["
          open = true
          next = false
        }
        if (next) {
          params += ", "
        }
        templateTypeParams.map(_.next()).foreach { typeParam =>
          if (typeParam.hasFlag(COVARIANT)) {
            params += "+"
          }
          if (typeParam.hasFlag(CONTRAVARIANT)) {
            params += "-"
          }
        }
        params += name
        params += boundsIn(bounds)
        next = true
      case _ =>
    }
    if (open) params += "]"

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
        params += textOf(children.head)
        if (node.hasFlag(HASDEFAULT)) {
          params += " = ???" // TODO parameter, /* compiled code */
        }
        next = true
      case _ =>
    }
    if (open) params += ")"
    if (template.isEmpty || definition.exists(it => it.hasFlag(CASE) && !it.hasFlag(OBJECT))) params else params.stripSuffix("()")
  }

  private def modifiersIn(node: Node, excluding: Set[Int] = Set.empty, isParameter: Boolean = true): String = { // TODO Optimize
    var s = ""
    if (node.hasFlag(OVERRIDE)) {
      s += "override "
    }
    if (node.hasFlag(PRIVATE)) {
      s += "private "
    } else if (node.hasFlag(PROTECTED)) {
      s += "protected "
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
    if (node.hasFlag(SEALED) && !excluding(SEALED)) {
      s += "sealed "
    }
    if (node.hasFlag(OPEN)) {
      s += "open "
    }
    if (node.hasFlag(TRANSPARENT)) {
      s += "transparent "
    }
    if (node.hasFlag(OPAQUE) && !excluding(OPAQUE)) {
      s += "opaque "
    }
    if (node.hasFlag(CASE) && !excluding(CASE)) {
      s += "case "
    }
    s
  }

  private def boundsIn(node: Node) = node match {
    case Node(TYPEBOUNDStpt, _, Seq(lower, upper)) =>
      var s = ""
      val l = textOf(lower)
      if (l.nonEmpty && l != "Nothing") {
        s += " >: " + l
      }
      val u = textOf(upper)
      if (u.nonEmpty && u != "Any") {
        s += " <: " + u
      }
      s
    case _ => throw new RuntimeException(node.toString)
  }
}
