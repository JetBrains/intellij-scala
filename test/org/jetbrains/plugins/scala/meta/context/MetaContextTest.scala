package org.jetbrains.plugins.scala.meta.context

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScMacroDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScMethodCallImpl
import org.jetbrains.plugins.scala.meta.TreeConverterTestBaseWithLibrary

import scala.meta.eval._
import scala.meta.internal.{ast => m, semantic => h}

class MetaContextTest extends TreeConverterTestBaseWithLibrary {

  def testSimple() {
    implicit val semanticContext = context
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
    val macroBody = context.ideaToMeta(macroDef)
    val macroName = macroBody match {
      case m.Defn.Macro(_, name, _, _, _, _) => name
    }
    val macroArgs = callImpl.args.exprs.toStream.map(context.ideaToMeta)
    val macroApplication = m.Term.Apply(macroName, macroArgs.asInstanceOf[scala.collection.immutable.Seq[m.Term]])
    val mMacroEnv = scala.collection.mutable.Map[m.Term.Name, Any]()
    try {
      val result = macroApplication.eval(mMacroEnv.toMap)
    } catch {
      case ex: Throwable =>
        ex.printStackTrace()
        org.junit.Assert.fail(ex.getMessage)
    }
  }

}
