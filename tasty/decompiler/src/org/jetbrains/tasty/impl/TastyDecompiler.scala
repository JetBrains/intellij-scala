package org.jetbrains.tasty.impl

import scala.quoted.show.SyntaxHighlight
import scala.tasty.compat._

class TastyDecompiler {
  def decompile(classpath: String, className: String): String = {
    var content: String = null

    val tastyConsumer = new TastyConsumer {
      override def apply(reflect: Reflection)(tree: reflect.Tree): Unit = {
        val codePrinter = new SourceCodePrinter[reflect.type](reflect)(SyntaxHighlight.plain)
        content = codePrinter.showTree(tree)(reflect.rootContext)
      }
    }

    val implementationClass = Class.forName("scala.tasty.compat.ConsumeTastyImpl")

    val consumeTasty = implementationClass.newInstance().asInstanceOf[ConsumeTasty]

    consumeTasty.apply(classpath, List(className), tastyConsumer)

    content
  }
}
