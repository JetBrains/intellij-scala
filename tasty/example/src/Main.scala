import java.io.File
import java.net.URLClassLoader

import scala.quoted.show.SyntaxHighlight
import scala.tasty.compat.{ConsumeTasty, Reflection, TastyConsumer}

// cd ~/IdeaProjects
// git clone https://github.com/lampepfl/dotty-example-project.git
// cd dotty-example-project ; sbt compile
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
    val Home = System.getProperty("user.home")
    val DottyExampleProject = Home + "/IdeaProjects/dotty-example-project"
    val Version = "0.27.0-RC1"

    val majorVersion = Version.split('.').take(2).mkString(".")

    val files = Seq(
      s"$Home/.cache/coursier/v1/https/repo1.maven.org/maven2/ch/epfl/lamp/dotty-interfaces/$Version/dotty-interfaces-$Version.jar",
      s"$Home/.cache/coursier/v1/https/repo1.maven.org/maven2/ch/epfl/lamp/dotty-compiler_$majorVersion//$Version/dotty-compiler_$majorVersion-$Version.jar",
      s"$Home/.cache/coursier/v1/https/repo1.maven.org/maven2/ch/epfl/lamp/dotty-tasty-inspector_$majorVersion//$Version/dotty-tasty-inspector_$majorVersion-$Version.jar",
      s"$Home/.cache/coursier/v1/https/repo1.maven.org/maven2/ch/epfl/lamp/tasty-core_$majorVersion//$Version/tasty-core_$majorVersion-$Version.jar",
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

    val consumeTasty = aClass.getDeclaredConstructor().newInstance().asInstanceOf[ConsumeTasty]

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

    assertExists(DottyExampleProject)

    val outputDir = DottyExampleProject + "/target/scala-" + majorVersion + "/classes"
    assertExists(outputDir)

    exampleClasses.foreach { fqn =>
      assertExists(outputDir + "/" + fqn.replace('.', '/') + ".class")
      assertExists(outputDir + "/" + fqn.replace('.', '/') + ".tasty")

      println(fqn)
      consumeTasty.apply(outputDir, List(fqn), tastyConsumer)
    }
  }

  private def assertExists(path: String): Unit = assert(new File(path).exists, path)
}
