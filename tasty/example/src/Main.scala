import java.io.File
import java.net.URLClassLoader

import scala.quoted.show.SyntaxHighlight
import scala.tasty.compat.{ConsumeTasty, Reflection, TastyConsumer}

object Main {
  val tastyConsumer: TastyConsumer = new TastyConsumer {
    override def apply(reflect: Reflection)(tree: reflect.delegate.Tree): Unit = {
      import reflect._

      val customExtractorsOutput = new ExtractorsPrinter[reflect.type](reflect).showTree(tree)(reflect.delegate.rootContext)
//      println(customExtractorsOutput)

      val originalExtractorsOutput = tree.showExtractors(reflect.delegate.rootContext)
      assert(customExtractorsOutput == originalExtractorsOutput)

      val customSourceCodeOutput = new SourceCodePrinter[reflect.type](reflect)(SyntaxHighlight.plain).showTree(tree)(reflect.delegate.rootContext)
//      println(customSourceCodeOutput)

      val originalSourceCodeOutput = tree.show(reflect.delegate.rootContext)
      assert(customSourceCodeOutput == originalSourceCodeOutput)
    }
  }

  def main(args: Array[String]): Unit = {
    val home = System.getProperty("user.home")

    val Version = "0.27.0-RC1"
    val MajorVersion = Version.split('.').take(2).mkString(".")

    val files = Seq(
      home + "/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.3/scala-library-2.13.3.jar",
      s"$home/.cache/coursier/v1/https/repo1.maven.org/maven2/ch/epfl/lamp/dotty-interfaces/$Version/dotty-interfaces-$Version.jar",
      s"$home/.cache/coursier/v1/https/repo1.maven.org/maven2/ch/epfl/lamp/dotty-library_$MajorVersion//$Version/dotty-library_$MajorVersion-$Version.jar",
      s"$home/.cache/coursier/v1/https/repo1.maven.org/maven2/ch/epfl/lamp/dotty-compiler_$MajorVersion//$Version/dotty-compiler_$MajorVersion-$Version.jar",
      s"$home/.cache/coursier/v1/https/repo1.maven.org/maven2/ch/epfl/lamp/dotty-tasty-inspector_$MajorVersion//$Version/dotty-tasty-inspector_$MajorVersion-$Version.jar",
      s"$home/.cache/coursier/v1/https/repo1.maven.org/maven2/ch/epfl/lamp/tasty-core_$MajorVersion//$Version/tasty-core_$MajorVersion-$Version.jar",
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
      "EnumTypes",
      "ImpliedInstances",
      "IntersectionTypes",
      "Main",
      "MultiversalEquality",
      "NamedTypeArguments",
//      "PatternMatching",
      "StructuralTypes",
      "TraitParams",
      "TypeLambdas",
      "UnionTypes",
    )

    exampleClasses.foreach { fqn =>
      println(fqn)
      consumeTasty.apply(home + "/IdeaProjects/dotty-example-project/target/scala-" + MajorVersion + "/classes", List(fqn), tastyConsumer)
    }
  }
}
