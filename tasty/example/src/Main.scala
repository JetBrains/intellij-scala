import java.io.File
import java.net.URLClassLoader

import scala.quoted.show.SyntaxHighlight
import scala.tasty.compat.{ConsumeTasty, Reflection, TastyConsumer}

object Main {
  val tastyConsumer: TastyConsumer = new TastyConsumer {
    override def apply(reflect: Reflection)(tree: reflect.Tree): Unit = {
      val codePrinter = new SourceCodePrinter[reflect.type](reflect)(SyntaxHighlight.plain)
      val customOutput = codePrinter.showTree(tree)(reflect.rootContext)
//      println(customOutput)

      import reflect._
      val originalOutput = tree.show(reflect.rootContext)
//      println(originalOutput)

      assert(customOutput == originalOutput)
    }
  }

  def main(args: Array[String]): Unit = {
    val home = System.getProperty("user.home")

    val Version = "0.26"
    val FullVersion = s"$Version.0-RC1"

    val files = Seq(
      home + "/.ivy2/cache/org.scala-lang/scala-library/jars/scala-library-2.13.2.jar",
      s"$home/.ivy2/cache/ch.epfl.lamp/dotty-interfaces/jars/dotty-interfaces-$FullVersion.jar",
      s"$home/.ivy2/cache/ch.epfl.lamp/dotty-library_$Version/jars/dotty-library_$Version-$FullVersion.jar",
      s"$home/.ivy2/cache/ch.epfl.lamp/dotty-compiler_$Version/jars/dotty-compiler_$Version-$FullVersion.jar",
      s"$home/.ivy2/cache/ch.epfl.lamp/dotty-tasty-inspector_$Version/jars/dotty-tasty-inspector_$Version-$FullVersion.jar",
      s"$home/.ivy2/cache/ch.epfl.lamp/tasty-core_$Version/jars/tasty-core_$Version-$FullVersion.jar",
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

//    consumeTasty.apply(s"$home/.ivy2/cache/ch.epfl.lamp/dotty-library_$Version/jars/dotty-library_$Version-$Version.0-RC1.jar",
//      List("scala.tasty.reflect.SourceCodePrinter"), tastyConsumer)

    val exampleClasses = Seq(
      "AutoParamTupling",
      "ContextQueries",
      "Conversion",
      "Conversion",
      "ImpliedInstances",
      "IntersectionTypes",
      "MultiversalEquality",
      "NamedTypeArguments",
      "PatternMatching",
      "StructuralTypes",
      "TraitParams",
      "TypeLambdas",
      "UnionTypes",
    )

    exampleClasses.foreach { fqn =>
      println(fqn)
      consumeTasty.apply(home + "/IdeaProjects/dotty-example-project/target/scala-" + Version + "/classes", List(fqn), tastyConsumer)
    }
  }
}
