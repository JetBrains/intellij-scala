package org.jetbrains.plugins.scala.lang.exprTree

class ExprTreePrinter(val withOrigin: Boolean = true) {
  private val builder = new StringBuilder
  private var curIndent = 0

  def print(tree: ExprTree): Unit = tree match {
    case ErrorExprTree(typeFailure, origin) =>
      printDirect(typeFailure.toString)
      printOrigin(origin)
    case LiteralExprTree(literalType, origin) =>
      printDirect(literalType.toString)
      printOrigin(origin)
    case FunctionLiteralExprTree(params, body, origin) =>
      printDirect(s"fun(${params.map(paramString).mkString(", ")})")
      inIndent {
        printOrigin(origin)
        printProp("body")
        print(body)
      }
    case underscore: UnderscoreReferenceExprTree =>
      printDirect(underscoreString(underscore.origin))
  }

  private def printOrigin(origin: ExprTreeOrigin): Unit =
    if (withOrigin) {
      printDirect(" [")
      printDirect(origin.toString)
      printDirect("]")
    }

  private def paramString(param: FunctionLiteralExprTree.Param): String = {
    param.origin match {
      case FunctionLiteralExprTree.ParamOrigin.Psi(psi) =>
        psi.name
      case underscoreInfo: FunctionLiteralExprTree.ParamOrigin.Underscore =>
        underscoreString(underscoreInfo)
    }
  }

  private def underscoreString(underscoreInfo: UnderscoreInfo): String =
    s"$$_${underscoreInfo.i}"

  private def printDirect(s: String): Unit = {
    builder.append(s)
  }

  private def printProp(prop: String): Unit = {
    printIndent()
    builder.append('•')
    builder.append(prop)
    builder.append(": ")
  }

  private def printIndent(indent: Int = curIndent): Unit = {
    builder.append('\n')
    builder.append("  " * indent)
  }

  private def inIndent[T](f: => T): T = {
    curIndent += 1
    try f
    finally curIndent -= 1
  }

  def result(): String = builder.toString()
}

object ExprTreePrinter {
  def print(tree: ExprTree): String = {
    val printer = new ExprTreePrinter(withOrigin = false)
    printer.print(tree)
    printer.result()
  }
}