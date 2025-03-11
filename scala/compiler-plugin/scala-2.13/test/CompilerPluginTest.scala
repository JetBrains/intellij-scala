import CompilerPluginTest._
import org.junit.{Assert, Test}

import java.nio.file.Files
import scala.reflect.internal.Reporter.{ERROR, INFO, Severity, WARNING}
import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.io.VirtualFile
import scala.tools.nsc.reporters.StoreReporter
import scala.tools.nsc.{Global, Settings}

class CompilerPluginTest {
  private final val Id =
    """object Macros {
      |  def id_impl(c: scala.reflect.macros.whitebox.Context)(x: c.Expr[Any]): c.Expr[Any] = x
      |  def id(x: Any): Any = macro id_impl
      |}""".stripMargin

  private def usage(s: String) =
    s"""object Usage {
       |  $s
       |}""".stripMargin

  // Types

  @Test def literalType(): Unit = assertMessagesAre(Id,
    usage("val v = Macros.id(123)"))(
    info("Macros.id(123)", tpe("123")))

  @Test def basicType(): Unit = assertMessagesAre(Id,
    usage("val v = Macros.id(123.abs)"))(
    info("Macros.id(123.abs)", tpe("Int")))

  @Test def typeDesignator(): Unit = assertMessagesAre(Id,
    usage("val v = Macros.id(null: scala.io.Source)"))(
    info("Macros.id(null: scala.io.Source)", tpe("scala.io.Source")))

  @Test def singletonType(): Unit = assertMessagesAre(Id,
    usage("val v = Macros.id(None)"))(
    info("Macros.id(None)", tpe("None.type")))

  @Test def parameterizedType(): Unit = assertMessagesAre(Id,
    usage("val v = Macros.id(Some(123))"))(
    info("Macros.id(Some(123))", tpe("Some[Int]")))

  @Test def javaObject(): Unit = assertMessagesAre(Id,
    usage("val v = Macros.id(new Object())"))(
    info("Macros.id(new Object())", tpe("Object")))

  @Test def scalaPackageObjectAlias(): Unit = assertMessagesAre(Id,
    usage("val v = Macros.id(Seq(1, 2, 3))"))(
    info("Macros.id(Seq(1, 2, 3))", tpe("Seq[Int]")))

  @Test def scalaPredefAlias(): Unit = assertMessagesAre(Id,
    usage("val v = Macros.id(Set(1, 2, 3))"))(
    info("Macros.id(Set(1, 2, 3))", tpe("scala.collection.immutable.Set[Int]")))

  // Expected type

  @Test def expectedType(): Unit = assertMessagesAre(Id,
    usage("val v = Macros.id(123): Int"))(
    info("Macros.id(123)", tpe("123")))

  // Multiple

  @Test def multipleTypes(): Unit = assertMessagesAre(Id,
    usage("val v1 = Macros.id(1); val v2 = Macros.id(2)"))(
    info("Macros.id(1)", tpe("1")), info("Macros.id(2)", tpe("2")))

  // Type parameter

  @Test def typeParameter(): Unit = assertMessagesAre(
    """object Macros {
      |  def id_impl[A](c: scala.reflect.macros.whitebox.Context)(x: c.Expr[A]): c.Expr[Any] = x
      |  def id[A](x: A): Any = macro id_impl[A]
      |}""".stripMargin,
    usage("val v = Macros.id(123)"))(
    info("Macros.id(123)", tpe("123")))

  // Complex

  @Test def complexMacro(): Unit = assertMessagesAre(
    s"""object Macros {
      |  def stringOrFile_impl(c: scala.reflect.macros.whitebox.Context)(x: c.Expr[Int]): c.Expr[Any] = {
      |    import c.universe._
      |    val Literal(Constant(value: Int)) = x.tree
      |    if (value == 1) reify(new String()) else reify(new java.io.File(""))
      |  }
      |  def stringOrFile(x: Int): Any = macro stringOrFile_impl
      |}""".stripMargin,
    usage("val v = Macros.stringOrFile(1)"))(
    info("Macros.stringOrFile(1)", tpe("String")))

  // Blackbox macro

  @Test def blackboxMacro(): Unit = assertMessagesAre(
    """object Macros {
      |  def id_impl(c: scala.reflect.macros.blackbox.Context)(x: c.Expr[Any]): c.Expr[Any] = x
      |  def id(x: Any): Any = macro id_impl
      |}""".stripMargin,
    usage("val v = Macros.id(123)"))(
    Seq.empty: _*)

  // Error handling

  @Test def lexerError(): Unit = assertMessagesAre(Id,
    usage("val x = '\\d'; val v = Macros.id(123)"))(
    error("", "invalid escape character"), info("Macros.id(123)", tpe("123")))

  @Test def parserError(): Unit = assertMessagesAre(Id,
    usage("class class; val v = Macros.id(123)"))(
    error("", "identifier expected but 'class' found."), info("Macros.id(123)", tpe("123")))

  @Test def typerError(): Unit = assertMessagesAre(Id,
    usage("val x = unknown; val v = Macros.id(123)"))(
    error("unknown", "not found: value unknown"), info("Macros.id(123)", tpe("123")))

  @Test def typeMismatchError(): Unit = assertMessagesAre(Id,
    usage("val v: String = Macros.id(123)"))(
    error("", "type mismatch;\n found   : Int(123)\n required: String"), info("Macros.id(123)", tpe("123")))

  @Test def typeInferenceError(): Unit = assertMessagesAre(Id,
    usage("val v = Macros.id(unknown)"))(
    error("unknown", "not found: value unknown"))
}

private object CompilerPluginTest {
  def tpe(s: String): String = "<type>" + s + "</type>"

  def info(position: String, text: String): String = message(INFO, position, text)

  def error(position: String, text: String): String = message(ERROR, position, text)

  private def message(level: Severity, position: String, text: String): String =
    s"[${ level match { case INFO => "info"; case WARNING => "warning"; case ERROR => "error" } }] $position: $text"

  def assertMessagesAre(code: String*)(messages: String*): Unit = {
    val info = compile(code)

    val actualMessages = info.map { it =>
      val position = new String(it.pos.source.content).substring(it.pos.start, it.pos.end)
      message(it.severity, position, it.msg)
    }

    Assert.assertEquals(messages.mkString("\n"), actualMessages.mkString("\n"))
  }

  private def compile(code: Seq[String]): Seq[StoreReporter.Info] = {
    // For some reason, VirtualDirectory (as well as VirtualDirectoryClassPath) works for regular sources but not for macros, using multiple runs
    val output = Files.createTempDirectory(getClass.getName).toFile
    try {
      compile(code, output.getAbsolutePath)
    } finally {
      output.listFiles().foreach(_.delete())
      output.delete()
    }
  }

  private def compile(code: Seq[String], output: String): Seq[StoreReporter.Info] = {
    val settings = new Settings()
    settings.usejavacp.value = true
    settings.language.value += settings.languageFeatures.macros
    settings.outputDirs.setSingleOutput(output)
    settings.classpath.append(output)

    val global = new Global(settings) { self =>
      override protected def computeInternalPhases(): Unit = {
        super.computeInternalPhases()
        phasesSet ++= new CompilerPlugin(this).components
      }
    }

    val reporter = new StoreReporter(settings)
    global.reporter = reporter

    val sources = code.zipWithIndex.map { case (s, i) => new BatchSourceFile(new VirtualFile(s"in-memory$i.scala"), s.toCharArray) }
    // Defining and using Scala 2 macros must be in different compiler runs
    sources.foreach { source =>
      val run = new global.Run()
      run.compileSources(List(source))
    }
    reporter.finish()

    reporter.infos.toSeq
  }
}
