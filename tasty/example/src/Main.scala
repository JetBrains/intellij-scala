import java.io.File
import java.net.URLClassLoader

import scala.quoted.show.SyntaxHighlight
import scala.tasty.compat.{ConsumeTasty, Reflection, TastyConsumer}

object Main {
  val tastyConsumer: TastyConsumer = new TastyConsumer {
    override def apply(reflect: Reflection)(tree: reflect.Tree): Unit = {
      val codePrinter = new SourceCodePrinter[reflect.type](reflect)(SyntaxHighlight.plain)
      println(codePrinter.showTree(tree)(reflect.rootContext))
    }
  }

  def main(args: Array[String]): Unit = {
    val home = System.getProperty("user.home")

    val files = Seq(
      home + "/.ivy2/cache/org.scala-lang/scala-library/jars/scala-library-2.13.1.jar",
      home + "/.ivy2/cache/ch.epfl.lamp/dotty-interfaces/jars/dotty-interfaces-0.22.0-RC1.jar",
      home + "/.ivy2/cache/ch.epfl.lamp/dotty-library_0.22/jars/dotty-library_0.22-0.22.0-RC1.jar",
      home + "/.ivy2/cache/ch.epfl.lamp/dotty-compiler_0.22/jars/dotty-compiler_0.22-0.22.0-RC1.jar",
      home + "/.ivy2/cache/ch.epfl.lamp/dotty-tasty-inspector_0.22/jars/dotty-tasty-inspector_0.22-0.22.0-RC1.jar",
      home + "/.ivy2/cache/ch.epfl.lamp/tasty-core_0.22/jars/tasty-core_0.22-0.22.0-RC1.jar",
      "target/plugin/Scala/lib/tasty/tasty-runtime.jar",
    )

    val urls = files.map(file => new File(file).toURI.toURL)

    urls.foreach(url => assert(new File(url.toURI).exists(), url.toString))

    val loader = new URLClassLoader(urls.toArray, getClass.getClassLoader)

//    Seq(
//      "scala.tasty.Reflection",
//      "scala.tasty.compat.Reflection",
//      "scala.tasty.inspector.TastyInspector",
//      "scala.tasty.compat.TastyInspector",
//      "scala.tasty.compat.ConsumeTasty",
//      "scala.tasty.compat.TastyConsumer",
//      "scala.tasty.compat.ConsumeTastyImpl")
//      .foreach(className => println(loader.loadClass(className).getClassLoader, className))

    val aClass = loader.loadClass("scala.tasty.compat.ConsumeTastyImpl")

    val consumeTasty = aClass.newInstance().asInstanceOf[ConsumeTasty]

    consumeTasty.apply(home + "/.ivy2/cache/ch.epfl.lamp/dotty-library_0.22/jars/dotty-library_0.22-0.22.0-RC1.jar",
      List("scala.tasty.reflect.SourceCodePrinter"), tastyConsumer)

  }
}
