package org.jetbrains.plugins.scala.statistics

import scala.language.implicitConversions

class FeatureKey private (val s: String) extends AnyVal

object FeatureKey {
  private implicit def toFK(s: String): FeatureKey = new FeatureKey(s)

  //Do not change values, it will invalidate already collected statistics

  final val renameLocal                       : FeatureKey = "scala.rename.local"
  final val renameMember                      : FeatureKey = "scala.rename.member"
  final val moveFile                          : FeatureKey = "scala.move.file"
  final val moveClass                         : FeatureKey = "scala.move.class"
  final val introduceVariable                 : FeatureKey = "scala.introduce.variable"
  final val introduceTypeAlias                : FeatureKey = "scala.introduce.type.alias"
  final val introduceField                    : FeatureKey = "scala.introduce.field"
  final val introduceParameter                : FeatureKey = "scala.introduce.parameter"
  final val extractMethod                     : FeatureKey = "scala.extract.method"
  final val extractTrait                      : FeatureKey = "scala.extract.trait"
  final val inline                            : FeatureKey = "scala.inline"
  final val changeSignature                   : FeatureKey = "scala.change.signature"

  final val showImplicitParameters            : FeatureKey = "scala.show.implicit.parameters"
  final val goToImplicitConversion            : FeatureKey = "scala.go.to.implicit.conversion"
  final val showTypeInfo                      : FeatureKey = "scala.show.type.info"
  final val structureView                     : FeatureKey = "scala.structure.view"
  final val optimizeImports                   : FeatureKey = "scala.optimize.imports"
  final val createFromUsage                   : FeatureKey = "scala.createFromUsage"
  final val overrideImplement                 : FeatureKey = "scala.overrideImplement"
  final val desugarCode                       : FeatureKey = "scala.desugar.code"

  final val structuralType                    : FeatureKey = "scala.structural.type"
  final val existentialType                   : FeatureKey = "scala.existential.type"

  final val runWorksheet                      : FeatureKey = "scala.worksheet"

  final def scFileModeSet(state: String)      : FeatureKey = s"scala.sc.file.set.$state"
  final def incrementalTypeSet(name: String)  : FeatureKey = s"scala.compiler.inc.type.set.$name"

  final val sbtShellCommand                   : FeatureKey = "scala.sbt.shell.execute.command"
  final val sbtShellTestCommand               : FeatureKey = "scala.sbt.shell.test.command"
  final val sbtShellTestRunConfig             : FeatureKey = "scala.sbt.shell.test.run.config"

  final val convertFromJavaText               : FeatureKey = "scala.convert.javatext"
  final val rearrange                         : FeatureKey = "scala.rearrange"
  final val macroDefinition                   : FeatureKey = "scala.macro.definition"

  final val annotatorTypeAware                : FeatureKey = "scala.file.with.type.aware.annotated"
  final val annotatorNotTypeAware             : FeatureKey = "scala.file.without.type.aware.annotated"
  final val collectionPackHighlighting        : FeatureKey = "scala.collection.pack.highlighting"
  final val parserScalaScript                 : FeatureKey = "scala.file.script.parsed"
  final val parserSsp                         : FeatureKey = "scala.ssp.parsing"

  final val scalaJsDynamicResolve             : FeatureKey = "scalajs.dynamic.resolve"
  final val scalaJsDynamicCompletion          : FeatureKey = "scalajs.dynamic.completion"

  final val debuggerTotal                     : FeatureKey = "scala.debugger"
  final val debuggerEvaluator                 : FeatureKey = "scala.debugger.evaluator"
  final val debuggerCompilingEvaluator        : FeatureKey = "scala.debugger.compiling.evaluator"
  final val debuggerLambdaBreakpoint          : FeatureKey = "scala.debugger.lambda.breakpoint"
  final val debuggerSmartStepInto             : FeatureKey = "scala.debugger.smart.step.into"
}
