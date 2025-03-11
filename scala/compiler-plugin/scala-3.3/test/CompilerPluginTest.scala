import CompilerPluginTest.*
import dotty.tools.dotc.Compiler
import dotty.tools.dotc.core.Contexts.ContextBase
import dotty.tools.dotc.core.{Contexts, Phases}
import dotty.tools.dotc.plugins.Plugins
import dotty.tools.dotc.reporting.{Diagnostic, StoreReporter}
import dotty.tools.dotc.util.SourceFile
import dotty.tools.io.VirtualFile
import org.junit.{Assert, Test}

class CompilerPluginTest {
  private final val Id =
    "transparent inline def id(x: Any): Any = x"

  // Types

  @Test def literalType(): Unit = assertMessagesAre(Id,
    "val _ = id(123)")(
    info("id(123)", tpe("123")))

  @Test def basicType(): Unit = assertMessagesAre(Id,
    "val _ = id(123.abs)")(
    info("id(123.abs)", tpe("_root_.scala.Int")))

  @Test def typeDesignator(): Unit = assertMessagesAre(Id,
    "val _ = id(null: scala.io.Source)")(
    info("id(null: scala.io.Source)", tpe("_root_.scala.io.Source")))

  @Test def singletonType(): Unit = assertMessagesAre(Id,
    "val _ = id(None)")(
    info("id(None)", tpe("_root_.scala.None.type")))

  @Test def parameterizedType(): Unit = assertMessagesAre(Id,
    "val _ = id(Some(123))")(
    info("id(Some(123))", tpe("_root_.scala.Some[_root_.scala.Int]")))

  @Test def javaObject(): Unit = assertMessagesAre(Id,
    "val _ = id(new Object())")(
    info("id(new Object())", tpe("_root_.java.lang.Object")))

  @Test def scalaPackageObjectAlias(): Unit = assertMessagesAre(Id,
    "val _ = id(Seq(1, 2, 3))")(
    info("id(Seq(1, 2, 3))", tpe("_root_.scala.collection.immutable.Seq[_root_.scala.Int]")))

  @Test def scalaPredefAlias(): Unit = assertMessagesAre(Id,
    "val _ = id(Set(1, 2, 3))")(
    info("id(Set(1, 2, 3))", tpe("_root_.scala.collection.immutable.Set[_root_.scala.Int]")))

  // Expected type

  @Test def expectedType(): Unit = assertMessagesAre(Id,
    "val _ = id(123): Int")(
    info("id(123)", tpe("123")))

  // Multiple

  @Test def multipleTypes(): Unit = assertMessagesAre(Id,
    "val _ = id(1); val _ = id(2)")(
    info("id(1)", tpe("1")), info("id(2)", tpe("2")))

  // Complex

  @Test def complexMethod(): Unit = assertMessagesAre(
    """transparent inline def stringOrFile(x: Int): Any =
      |  if (x == 1) new String()
      |  else new java.io.File("")
      |val _ = stringOrFile(1)""".stripMargin)(
    info("stringOrFile(1)", tpe("_root_.java.lang.String")))

  // Non-transparent method

  @Test def nontransparent(): Unit = assertMessagesAre("inline def id(x: Any): Any = x",
    "val _ = id(123)")(
    Seq.empty: _*)

  // Error handling

  @Test def lexerError(): Unit = assertMessagesAre(Id,
    "val _ = '\\d'; val _ = id(123)")(
    error("", "invalid escape character"), info("id(123)", tpe("123")))

  @Test def parserError(): Unit = assertMessagesAre(Id,
    "class class; val _ = id(123)")(
    error("class", "an identifier expected, but 'class' found"), info("id(123)", tpe("123")))

  @Test def typerError(): Unit = assertMessagesAre(Id,
    "val _ = unknown; val _ = id(123)")(
    error("unknown", "Not found: unknown"), info("id(123)", tpe("123")))

  @Test def typeMismatchError(): Unit = assertMessagesAre(Id,
    "val _: String = id(123)")(
    error("id(123)", "Found:    (123 : Int)\nRequired: String"), info("id(123)", tpe("123")))

  @Test def typeInferenceError(): Unit = assertMessagesAre(Id,
    "val _ = id(unknown)")(
    error("unknown", "Not found: unknown"))
}

private object CompilerPluginTest {
  def tpe(s: String): String = "<type>" + s + "</type>"

  def info(position: String, text: String): String = message(0, position, text)

  def error(position: String, text: String): String = message(2, position, text)

  private def message(level: Int, position: String, text: String): String =
    s"[${ level match { case 0 => "info"; case 1 => "warning"; case 2 => "error" } }] $position: $text"

  def assertMessagesAre(code: String*)(messages: String*): Unit = {
    val info = compile(code)

    val actualMessages = info.map { it =>
      val position = new String(it.pos.source.content()).substring(it.pos.start, it.pos.end)
      message(it.level, position, it.msg.message)
    }

    Assert.assertEquals(messages.mkString("\n"), actualMessages.mkString("\n"))
  }

  private def compile(code: Seq[String]): Seq[Diagnostic] = {
    val reporter = new StoreReporter()

    val pluginPhases = new CompilerPlugin().init(List.empty)

    val context = {
      val contextBase = new ContextBase() {
        override def addPluginPhases(plan: List[List[Phases.Phase]])(using Contexts.Context): List[List[Phases.Phase]] =
          Plugins.schedule(plan, pluginPhases)
      }
      val ctx = contextBase.initialCtx
      ctx.fresh
        .setReporter(reporter)
        .setSetting(ctx.settings.usejavacp, true)
        .setSetting(ctx.settings.color, "never")
        .setSetting(ctx.settings.YstopAfter, pluginPhases.map(_.phaseName))
    }

    val compiler = new Compiler()

    val sources = code.zipWithIndex.map((s, i) => new SourceFile(new VirtualFile(s"in-memory$i.scala"), s.toCharArray))
    val run = compiler.newRun(using context)
    run.compileSources(sources.toList)

    reporter.pendingMessages(using context)
  }
}
