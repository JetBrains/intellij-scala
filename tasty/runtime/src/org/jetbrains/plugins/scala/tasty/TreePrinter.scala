package org.jetbrains.plugins.scala.tasty

import dotty.tools.tasty.TastyFormat._

// TODO refactor
// TODO use StringBuilder
object TreePrinter {

  def textOf(node: Node, definition: Option[Node] = None): String = node match {
    case Node(PACKAGE, _, Seq(Node(TERMREFpkg, Seq(name), _), tail: _*)) =>
      "package " + name + "\n" +
        "\n" +
        tail.map(textOf(_)).filter(_.nonEmpty).mkString("\n")

    case node @ Node(TYPEDEF, Seq(name), Seq(template, _: _*)) if !node.hasFlag(SYNTHETIC) =>
      val isObject = node.hasFlag(OBJECT)
      val isImplicitClass = !isObject && node.nextSibling.exists(it => it.is(DEFDEF) && it.hasFlag(SYNTHETIC) && it.name == name)
      val keyword = if (isObject) "object " else (if (node.hasFlag(TRAIT)) "trait " else "class ")
      val identifier = if (isObject) node.previousSibling.fold(name)(_.name) else name
      val modifiers = modifiersIn(if (isObject) node.previousSibling.getOrElse(node) else node) + (if (isImplicitClass) "implicit " else "")
      modifiers + keyword + identifier + textOf(template, Some(node))

    case node @ Node(TEMPLATE, _, children) => // TODO method?
      val primaryConstructor = children.find(it => it.is(DEFDEF) && it.names == Seq("<init>"))
      val modifiers = primaryConstructor.map(modifiersIn(_)).map(it => if (it.nonEmpty) " " + it else "").getOrElse("")
      val parameters = primaryConstructor.map(it => parametersIn(it, Some(node), definition)).getOrElse("")
      val members = children.filter(it => it.is(DEFDEF, VALDEF, TYPEDEF) && !primaryConstructor.contains(it))  // TODO type member
        .map(textOf(_)).filter(_.nonEmpty).map(indent).mkString("\n\n")
      (modifiers + (if (modifiers.nonEmpty && parameters.isEmpty) "()" else parameters)) +
        (if (members.isEmpty) "" else " {\n" + members + "\n}")

    case node @ Node(DEFDEF, Seq(name), children) if !node.hasFlag(FIELDaccessor) && !node.hasFlag(SYNTHETIC) =>
      val isDeclaration = children.filter(!_.isModifier).lastOption.exists(_.isTypeTree)
      val tpe = children.find(_.isTypeTree)
      children.filter(_.is(EMPTYCLAUSE, PARAM))
      if (name == "<init>") {
        modifiersIn(node) + "def this" + parametersIn(node) + " = this(???)" // TODO parameter, this(/* compiled code */)
      } else {
        modifiersIn(node) + "def " + name + parametersIn(node) + ": " + tpe.map(textOf(_)).getOrElse("") + (if (isDeclaration) "" else " = ???") // TODO parameter, /* compiled code */
      }

    case node @ Node(VALDEF, Seq(name), children) if !node.hasFlag(SYNTHETIC) && !node.hasFlag(OBJECT) =>
      val isDeclaration = children.filter(!_.isModifier).lastOption.exists(_.isTypeTree)
      val tpe = children.find(_.isTypeTree)
      modifiersIn(node) + (if (node.hasFlag(MUTABLE)) "var " else "val ") + name + ": " + tpe.map(textOf(_)).getOrElse("") + (if (isDeclaration ) "" else " = ???") // TODO parameter

    // TODO method?
    case Node(IDENTtpt, Seq(name), _) => name
    case Node(TYPEREF, Seq(name), _) => name
    case Node(APPLIEDtpt, _, Seq(constructor, arguments: _*)) => textOf(constructor) + "[" + arguments.map(textOf(_)).mkString(", ") + "]"

    case _ => ""

  }

  private def indent(s: String): String = s.split("\n").map("  " + _).mkString("\n") // TODO use indent: Int parameter instead

  private def parametersIn(node: Node, template: Option[Node] = None, definition: Option[Node] = None): String = {
    var params = ""
    var open = false
    var next = false

    val typeParams = template.map(_.children.filter(_.is(TYPEPARAM)).iterator)
    val valueParams = template.map(_.children.filter(_.is(PARAM)).iterator)

    node.children.foreach {
      case Node(TYPEPARAM, Seq(name), Seq(Node(TYPEBOUNDStpt, _, Seq(lower, upper)))) =>
        if (!open) {
          params += "["
          open = true
          next = false
        }
        if (next) {
          params += ", "
        }
        typeParams.map(_.next()).foreach { typeParam =>
          if (typeParam.hasFlag(COVARIANT)) {
            params += "+"
          }
          if (typeParam.hasFlag(CONTRAVARIANT)) {
            params += "-"
          }
        }
        params += name
        val l = textOf(lower)
        if (l.nonEmpty && l != "Nothing") {
          params += " >: " + l
        }
        val u = textOf(upper)
        if (u.nonEmpty && u != "Any") {
          params += " <: " + u
        }
        next = true
      case _ =>
    }
    if (open) params += "]"

    open = false
    next = false

    node.children.foreach {
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
        valueParams.map(_.next()).foreach { valueParam =>
          if (!valueParam.hasFlag(LOCAL)) {
            params += modifiersIn(valueParam, withImplicit = false)
            if (valueParam.hasFlag(MUTABLE)) {
              params += "var "
            } else {
              if (!(definition.exists(_.hasFlag(CASE)) && valueParam.flags.size == 1)) {
                params += "val "
              }
            }
          }
        }
        params += name + ": " + textOf(children.head)
        next = true
      case _ =>
    }
    if (open) params += ")"
    if (template.isEmpty || definition.exists(it => it.hasFlag(CASE))) params else params.stripSuffix("()")
  }

  private def modifiersIn(node: Node, withImplicit: Boolean = true): String = { // TODO Optimize
    var s = ""
    if (node.hasFlag(OVERRIDE)) {
      s += "override "
    }
    if (node.hasFlag(PRIVATE)) {
      s += "private "
    } else if (node.hasFlag(PROTECTED)) {
      s += "protected "
    }
    if (node.hasFlag(GIVEN)) {
      s += "using "
    }
    if (node.hasFlag(IMPLICIT)) {
      s += "implicit "
    }
    if (node.hasFlag(FINAL)) {
      s += "final "
    }
    if (node.hasFlag(LAZY)) {
      s += "lazy "
    }
    if (node.hasFlag(ABSTRACT)) {
      s += "abstract "
    }
    if (node.hasFlag(SEALED)) {
      s += "sealed "
    }
    if (node.hasFlag(OPEN)) {
      s += "open "
    }
    if (node.hasFlag(CASE)) {
      s += "case "
    }
    s
  }
}
