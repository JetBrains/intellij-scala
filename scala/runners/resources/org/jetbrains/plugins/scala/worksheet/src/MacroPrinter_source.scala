package org.jetbrains.plugins.scala.worksheet

import scala.language.experimental.macros
import scala.reflect.macros._

object MacroPrinter {
  def printDefInfo[T](toPrint: T): String = macro printDefImpl[T]

  def printTypeInfo[T]: String = macro printTypeInfoImpl[T]

  /**
   * Usage
   * {{{
   *   printImportInfo({import java.io.File;})
   * }}}
   */
  def printImportInfo[T](toPrint: T): String = macro printImportInfoImpl[T]

  def printGeneric[T](toPrint: T): String = macro printGenericImpl[T]

  def printDefImpl[T: c.WeakTypeTag](c: blackbox.Context)(toPrint: c.Expr[T]): c.universe.Tree = {
    import c.universe._

    q"${toPrint.tree.tpe.toString}"
  }

  def printTypeInfoImpl[T](c: blackbox.Context)(implicit ev: c.WeakTypeTag[T]): c.universe.Tree = {
    import c.universe._

    q"${ev.tpe.toString}"
  }

  def printImportInfoImpl[T: c.WeakTypeTag](c: blackbox.Context)(toPrint: c.Expr[T]): c.universe.Tree = {
    import c.universe._

    toPrint.tree match {
      case c.universe.Block(imp, _) => q"${imp.head.toString()}"
    }
  }

  def printGenericImpl[T: c.WeakTypeTag](c: blackbox.Context)(toPrint: c.Expr[T]): c.universe.Tree = {
    import c.universe._

    val u = c.universe

    val e = toPrint.tree match {
      case c.universe.Block(imp, _) =>
        Option(imp.apply(1)) flatMap {
          case defdef: c.universe.DefDef =>
            val a = s"${u.show(defdef.name)}${
              defdef.tparams.map {
                case tp =>
                  u.show(tp, true, false, false, false).stripPrefix("type ")
              }.mkString("[", ",", "]")
            }${defdef.vparamss.map {
              case vparams =>
                vparams.map {
                  case param => show(param, false, true, false, false).stripSuffix(" = _")
                }.mkString("(", ",", ")")
            }.mkString("")} => ${defdef.tpt.toString()}"

            Some(a)
          case _ => None
        }
      case _ => None
    }

    q"${e getOrElse ""}"
  }
}

class MacroPrinter {}