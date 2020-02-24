package org.jetbrains.tasty.impl

import scala.quoted.show.SyntaxHighlight
import scala.tasty.compat._

class TastyDecompiler {
  def decompile(): String = {
    var content: String = null

//    val inspector = new TastyInspector {
//      override def processCompilationUnit0(reflect: Reflection)(tree: reflect.Tree): Unit = {
//        val codePrinter = new SourceCodePrinter[reflect.type](reflect)(SyntaxHighlight.plain)
//        content = codePrinter.showTree(tree)(reflect.rootContext)
//
////        val extractorPrinter = new ExtractorsPrinter[reflect.type](reflect)
////        println(extractorPrinter.showTree(tree)(reflect.rootContext))
////        println(content)
//      }
//    }
//    inspector.inspect0("/home/pavel/IdeaProjects/dotty-example-project/target/scala-0.22/classes", List("ImpliedInstances"))
//    content
    ""
  }
}
