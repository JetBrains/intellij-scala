package org.jetbrains.plugins.scala.lang.exprTree

class ExprTreePrinter(val withOrigin: Boolean = true) {
  private val builder = new StringBuilder
  private var curIndent = 0
  private var lastWasProp = false

  def print(tree: ExprTree): Unit = tree match {
    case ErrorExprTree(typeFailure, origin) =>
      printDirect(s"err:${typeFailure.toString}")
      printOrigin(origin)
    case LiteralExprTree(literalType, origin) =>
      printDirect(s"lit:${literalType.toString}")
      printOrigin(origin)
    case FunctionLiteralExprTree(params, body, origin) =>
      printDirect(s"fun(${params.map(paramString).mkString(", ")})")
      printOrigin(origin)
      inIndent {
        printProp("body")
        print(body)
      }
    case UnqualifiedRefExprTree(refName, origin) =>
      printDirect(s"ref:$refName")
      printOrigin(origin)
    case QualifiedRefExprTree(refName, qualifier, origin) =>
      printDirect(s"ref:$refName")
      printOrigin(origin)
      inIndent {
        printProp("qual")
        print(qualifier)
      }
    case underscore: UnderscoreReferenceExprTree =>
      printDirect(underscoreString(underscore.origin))
    case CallExprTree(target, argLists, origin) =>
      printDirect("call")
      printOrigin(origin)
      inIndent {
        printProp("target")
        print(target)
        printProp("args")
        inIndent {
          import CallExprTree.ArgList
          argLists.foreach {
            case ArgList.Types(types, origin) =>
              printProp("typeArgs")
              printOrigin(origin)
              inIndent {
                types.foreach {
                  case Right(tpe) =>
                    printPropRaw(s"type:${tpe.toString}")
                  case Left(failure) =>
                    printPropRaw(s"err:${failure.toString}")
                }
              }
            case ArgList.Values(args, isUsing, origin) =>
              val usingText = if (isUsing) " (using)" else ""
              printProp("valueArgs" + usingText)
              if (args.isEmpty) {
                printDirect("(empty)")
              }
              printOrigin(origin)
              inIndent {
                import CallExprTree.Arg
                args.zipWithIndex.foreach {
                  case (Arg.Positional(arg), i) =>
                    printProp(i.toString)
                    print(arg)
                  case (Arg.Named(name, arg, origin), _) =>
                    printProp(name)
                    printOrigin(origin)
                    print(arg)
                }
              }
          }
        }
      }
  }

  private def printOrigin(origin: Any): Unit =
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
    if (lastWasProp) {
      builder.append(' ')
      lastWasProp = false
    }
    builder.append(s)
  }

  private def printProp(prop: String): Unit = {
    printPropRaw(prop)
    builder.append(":")
    lastWasProp = true
  }

  private def printPropRaw(prop: String): Unit = {
    printIndent()
    builder.append('•')
    builder.append(prop)
  }

  private def printIndent(indent: Int = curIndent): Unit = {
    lastWasProp = false
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