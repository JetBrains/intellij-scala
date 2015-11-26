package scala.meta.context

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScMacroDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScMethodCallImpl

import scala.meta.TreeConverterTestBaseWithLibrary

import scala.meta.internal.{ast => m, semantic => h}

class MetaContextTest extends TreeConverterTestBaseWithLibrary {

  def testSimple() {
    import scala.meta._
    implicit val c = semanticContext
    val body =
      """
        |val txt = "hello, world"
        |
        |def hello(str: String): Unit = macro {
        |  q"println($str)"
        |}
        |
        |//start
        |hello(txt)
      """.stripMargin

    val callImpl = psiFromText(body).asInstanceOf[ScMethodCallImpl]

    val macroDef = callImpl.getEffectiveInvokedExpr match {
      case e: ScReferenceExpression => e.resolve().asInstanceOf[ScMacroDefinition]
    }
    val macroBody = semanticContext.ideaToMeta(macroDef)
    val macroName = macroBody match {
      case m.Defn.Macro(_, name, _, _, _, _) => name
    }
    val macroArgs = callImpl.args.exprs.toStream.map(semanticContext.ideaToMeta)
    val macroApplication = m.Term.Apply(macroName, macroArgs.asInstanceOf[scala.collection.immutable.Seq[m.Term]])
    val mMacroEnv = scala.collection.mutable.Map[m.Term.Name, Any]()
    try {
      val result = macroApplication //.eval(mMacroEnv.toMap)
    } catch {
      case ex: Throwable =>
        ex.printStackTrace()
        org.junit.Assert.fail(ex.getMessage)
    }
  }

  def extractClass(t: m.Tree): m.Defn.Class = {
    t match {
      case c@m.Defn.Class(_, name, _, _, m.Template(_, _, _, Some(stats))) =>
        stats.last match {
          case cl: m.Defn.Class => cl
        }
    }
  }

  def testParents1() {
    doTest
  }

  def doTest: Unit = {
    import scala.meta._
    implicit val sc:scala.meta.semantic.Context = semanticContext
    val text =
      """
        | class A {
        | class Foo[T] { def foo(a: Int) = a }
        | class Bar extends Foo[Int]
        | }
      """.stripMargin

    val bar = extractClass(convert(text))
    val foo = bar.tpe.supertypes.head
    val members = foo.members
    val denot = foo.show[Semantics]
    ""
  }
}
