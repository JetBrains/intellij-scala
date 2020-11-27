import java.io.File
import java.net.URLClassLoader

import scala.quoted.Reflection
import scala.tasty.inspector.{ConsumeTasty, TastyConsumer}

// cd ~/IdeaProjects
// git clone https://github.com/lampepfl/dotty-example-project.git
// cd dotty-example-project ; sbt compile
object Main {
  val tastyConsumer: TastyConsumer = new TastyConsumer {
    override def apply(reflect: Reflection)(tree: reflect.delegate.Tree): Unit = {
      import reflect._

      val customExtractorsOutput = new ExtractorsPrinter[reflect.type](reflect).showTree(tree)
//      println(customExtractorsOutput)

      val originalExtractorsOutput = tree.showExtractors
      assert(customExtractorsOutput.startsWith("PackageClause("))
      assert(customExtractorsOutput == originalExtractorsOutput)

      val customSourceCodeOutput = new SourceCodePrinter[reflect.type](reflect).showTree(tree)
//      println(customSourceCodeOutput)

      val originalSourceCodeOutput = tree.show
      assert(originalSourceCodeOutput.startsWith("import ") || originalSourceCodeOutput.startsWith("@scala.annotation.internal.SourceFile("))
      assert(customSourceCodeOutput == originalSourceCodeOutput)
    }
  }

  def main(args: Array[String]): Unit = {
    val Home = System.getProperty("user.home")
    val DottyExampleProject = Home + "/IdeaProjects/dotty-example-project"
    val Version = "3.0.0-M2"

    val Repository = s"$Home/.cache/coursier/v1/https/repo1.maven.org/maven2"

    val files = Seq(
      s"$Repository/org/scala-lang/scala3-interfaces/$Version/scala3-interfaces-$Version.jar",
      s"$Repository/org/scala-lang/scala3-compiler_$Version//$Version/scala3-compiler_$Version-$Version.jar",
      s"$Repository/org/scala-lang/scala3-tasty-inspector_$Version//$Version/scala3-tasty-inspector_$Version-$Version.jar",
      s"$Repository/org/scala-lang/tasty-core_$Version/$Version/tasty-core_$Version-$Version.jar",
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
//      "PatternMatching",
      "StructuralTypes",
      "TraitParams",
      "TypeLambdas",
      "UnionTypes",
    )

    assertExists(DottyExampleProject)

    val outputDir = DottyExampleProject + "/target/scala-" + Version + "/classes"
    assertExists(outputDir)

    exampleClasses.foreach { fqn =>
      println(fqn)

      assertExists(outputDir + "/" + fqn.replace('.', '/') + ".tasty")
      assertExists(outputDir + "/" + fqn.replace('.', '/') + ".class")

      consumeTasty.apply(outputDir, List(s"$outputDir/$fqn.tasty"), tastyConsumer)
    }
  }

  private def assertExists(path: String): Unit = assert(new File(path).exists, path)
}
