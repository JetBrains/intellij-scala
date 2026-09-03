package org.jetbrains.plugins.scala.semantic

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.semantic.SemanticTestBase.given
import org.junit.Test

class AkkaParsingSemanticTest extends SemanticTestBase("com.typesafe.akka" %% "akka-parsing" % "10.5.3", "com.typesafe.akka" %% "akka-actor" % "2.8.8" /*% Provided*/)("akka.macros", "akka.parboiled2") {
//  @Test def single(): Unit = doTest("")

  @Test def test(): Unit = doTest("""
    akka.macros.LogHelper
    //akka.macros.LogHelperMacro
    //akka.parboiled2.CharPredicate
    //akka.parboiled2.CharUtils
    //akka.parboiled2.DynamicRuleDispatch
    //akka.parboiled2.DynamicRuleDispatchMacro
    akka.parboiled2.DynamicRuleHandler
    //akka.parboiled2.ErrorFormatter
    //akka.parboiled2.ParseError
    //akka.parboiled2.Parser
    //akka.parboiled2.ParserInput
    //akka.parboiled2.ParserMacroMethods
    //akka.parboiled2.Position
    akka.parboiled2.Repeated
    //akka.parboiled2.Rule
    akka.parboiled2.RuleDSL
    //akka.parboiled2.RuleDSLActions
    //akka.parboiled2.RuleDSLBasics
    //akka.parboiled2.RuleDSLCombinators
    //akka.parboiled2.RuleRunnable
    akka.parboiled2.RuleRunner
    //akka.parboiled2.RuleTrace
    akka.parboiled2.RuleX
    //akka.parboiled2.ValueStack
    akka.parboiled2.ValueStackOverflowException
    akka.parboiled2.ValueStackUnderflowException
    //akka.parboiled2.support.ActionOps
    akka.parboiled2.support.AlternativeUnpacks
    //akka.parboiled2.support.FCapture
    //akka.parboiled2.support.HListable
    //akka.parboiled2.support.Join
    //akka.parboiled2.support.LowPrioJoin
    //akka.parboiled2.support.LowerPriorityLifter
    //akka.parboiled2.support.RunResult
    //akka.parboiled2.support.TailSwitch
    //akka.parboiled2.support.Unpack
    //akka.parboiled2.support.hlist.::
    //akka.parboiled2.support.hlist.HList
    akka.parboiled2.support.hlist.HNil
    //akka.parboiled2.support.hlist.ops.hlist
    akka.parboiled2.support.hlist.syntax.HListOps
    //akka.parboiled2.util.Base64
  """)
}