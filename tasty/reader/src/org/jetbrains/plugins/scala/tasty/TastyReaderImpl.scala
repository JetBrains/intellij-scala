package org.jetbrains.plugins.scala.tasty

import org.jetbrains.annotations.Nullable

import scala.quoted.show.SyntaxHighlight
import scala.tasty.compat._

// TODO Load in the same classloader when the project will use Scala 2.13
class TastyReaderImpl {
  @Nullable def read(classpath: String, className: String): TastyFile = {
    // TODO An ability to detect errors, https://github.com/lampepfl/dotty-feature-requests/issues/101
    var result: TastyFile = null

    val tastyConsumer = new TastyConsumer {
      override def apply(reflect: Reflection)(tree: reflect.Tree): Unit = {
        val printer = new SourceCodePrinter[reflect.type](reflect)(SyntaxHighlight.plain)
        result = TastyFile(printer.showTree(tree)(reflect.rootContext), printer.references.toArray, printer.types.toArray)
      }
    }
    val implementationClass = Class.forName("scala.tasty.compat.ConsumeTastyImpl")
    val consumeTasty = implementationClass.newInstance().asInstanceOf[ConsumeTasty]
    consumeTasty.apply(classpath, List(className), tastyConsumer)

    result
  }
}
