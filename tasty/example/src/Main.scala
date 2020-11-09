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
    val home = System.getProperty("user.home")
//    val coursierCacheFolder = s"$home/AppData/Local/Coursier/cache" // Windows 1
//    val coursierCacheFolder = s"$home/.coursier/cache" // Windows 2
    val coursierCacheFolder = s"$home/.cache/coursier" // Unix
    val mavenCacheRoot = s"$coursierCacheFolder/v1/https/repo1.maven.org/maven2"

    val Version = "3.0.0-M1"
    val MajorVersion = Version // it is the same for "3.0.0-M1"
    //val MajorVersion = Version.split('.').take(2).mkString(".")

    val files = Seq(
      s"$mavenCacheRoot/org/scala-lang/scala3-interfaces/$Version/scala3-interfaces-$Version.jar",
      s"$mavenCacheRoot/org/scala-lang/scala3-compiler_$MajorVersion/$Version/scala3-compiler_$MajorVersion-$Version.jar",
      s"$mavenCacheRoot/org/scala-lang/scala3-tasty-inspector_$MajorVersion//$Version/scala3-tasty-inspector_$MajorVersion-$Version.jar",
      s"$mavenCacheRoot/org/scala-lang/tasty-core_$MajorVersion//$Version/tasty-core_$MajorVersion-$Version.jar",
      "target/plugin/Scala/lib/tasty/tasty-runtime.jar",
    )

    val urls = files.map(file => new File(file).toURI.toURL)

    println("Creating classloader for jars:")
    urls.foreach { url =>
      assert(new File(url.toURI).exists(), url.toString)
      println("  " + url)
    }

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
//      "NamedTypeArguments",
//      "PatternMatching",
      "StructuralTypes",
      "TraitParams",
      "TypeLambdas",
      "UnionTypes",
    )


    val exampleProject = new File(home + "/IdeaProjects/dotty-example-project/")
    assert(exampleProject.exists(), s"Dotty example project doesn't exist under ${exampleProject.toPath}")

    val outputDir = new File(exampleProject, "target/scala-" + MajorVersion + "/classes")
    assert(outputDir.exists(), s"Dotty example project classes are not compiled: ${outputDir}")

    exampleClasses.foreach { fqn =>
      println(fqn)
      assertExists(s"$outputDir/${fqn.replace('.', '/')}.class")
      assertExists(s"$outputDir/${fqn.replace('.', '/')}.tasty")

      val classes = outputDir.getAbsolutePath
      consumeTasty.apply(classes, List(fqn), tastyConsumer)
    }
  }

  private def assertExists(path: String): Unit = assert(new File(path).exists, path)
}
