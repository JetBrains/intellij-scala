package org.jetbrains.plugins.scala.worksheet

import scala.language.experimental.macros
import scala.reflect.macros.Context

/**
 * User: Dmitry Naydanov
 * Date: 1/21/14
 */
object MacroPrinter {
  def printDefInfo[T](toPrint: T) = macro printDefImpl[T]

  def printTypeInfo[T] = macro printTypeInfoImpl[T]

  /**
   * Usage 
   * {{{
   *   printImportInfo({import java.io.File;})
   * }}}
   */
  def printImportInfo[T](toPrint: T) = macro printImportInfoImpl[T]

  def printGeneric[T](toPrint: T) = macro printGenericImpl[T]

  def printDefImpl[T: c.WeakTypeTag](c: Context)(toPrint: c.Expr[T]) = c literal toPrint.tree.tpe.toString

  def printTypeInfoImpl[T](c: Context)(implicit ev: c.WeakTypeTag[T]) = c literal ev.tpe.toString

  def printImportInfoImpl[T: c.WeakTypeTag](c: Context)(toPrint: c.Expr[T]) = {
    (toPrint.tree: @unchecked) match {
      case c.universe.Block(imp, _) => c literal imp.head.toString()
    }
  }

   def printGenericImpl[T: c.WeakTypeTag](c: Context)(toPrint: c.Expr[T]): c.Expr[String] = {
     val u = c.universe
     import u._

     val e = (toPrint.tree: @unchecked) match {
       case c.universe.Block(imp, _) =>
         Option(imp.apply(1)) flatMap {
           case defdef: c.universe.DefDef @unchecked =>
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

     c literal e.getOrElse("")
   }
}

class MacroPrinter {}