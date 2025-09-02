package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.structuralsearch.PredefinedConfigurationUtil.createConfiguration
import com.intellij.structuralsearch.plugin.ui.Configuration
import org.jetbrains.plugins.scala.ScalaFileType

object ScalaPredefinedConfigurations {
  def createPredefinedTemplated(): Array[Configuration] = {
    Seq(
      createConfiguration("Any class", "classany",
        "class '_name { }",
        classFolder, ScalaFileType.INSTANCE),
      createConfiguration("Class with selected primary constructor", "classprimary",
        "class '_name(var '_arg1\\: '_ty, val '_arg2\\: '_ty2) { }",
        classFolder, ScalaFileType.INSTANCE),
      createConfiguration("Class with parent classes", "classparent",
        "class '_name extends '_parent1, '_parent2 { }",
        classFolder, ScalaFileType.INSTANCE),
      createConfiguration("Class with property", "classprop",
        """class '_class {
          | var '_a\: '_tya
          | var '_b\: '_tyb
          |}""".stripMargin,
        classFolder, ScalaFileType.INSTANCE),
      createConfiguration("Class with function(s)", "classprop",
        """class '_class {
          | def '_func('_arg*\: '_ty): '_ret = { '_body* }
          |}""".stripMargin,
        classFolder, ScalaFileType.INSTANCE),
      createConfiguration("All value properties of a class", "classvalprops",
        """class '_class {
          | val 'prop\: '_ty{0,1} = '_expr{0,1}
          |}""".stripMargin,
        classFolder, ScalaFileType.INSTANCE),
      createConfiguration("All variable properties of a class", "classvalprops",
        """class '_class {
          | var 'prop\: '_ty{0,1} = '_expr{0,1}
          |}""".stripMargin,
        classFolder, ScalaFileType.INSTANCE),
      createConfiguration("All functions of a class", "classvalprops",
        """class '_class {
          | def 'func('_para*\: '_ty{0,1}): '_ret{0,1} = {
          |   '_body*
          | }
          |}""".stripMargin,
        classFolder, ScalaFileType.INSTANCE),
      createConfiguration("Any function", "functionany",
        "def '_func('_arg*\\: '_ty): '_ret { '_b* }",
        funcFolder, ScalaFileType.INSTANCE),
      createConfiguration("Functions without return type", "functionnotret",
        "def '_func('_arg*\\: '_ty): '_ret{0,0} { '_b* }",
        funcFolder, ScalaFileType.INSTANCE),
      createConfiguration("Functions with annotation", "functionannot",
        "@'_anno\ndef '_name('_arg*\\: '_ty): '_ret{0,0} { '_b* }",
        funcFolder, ScalaFileType.INSTANCE),
      createConfiguration("Any if-else expression", "ifany",
        "if ('_cond) '_then*\nelse '_else*\n",
        exprFolder, ScalaFileType.INSTANCE),
      createConfiguration("Any if expression", "ifany",
        "if ('_cond) '_then*\n",
        exprFolder, ScalaFileType.INSTANCE),
      createConfiguration("Any match expression", "matchany",
        """'_expr match {
          |  case '_pattern* => '_res
          |}""".stripMargin,
        exprFolder, ScalaFileType.INSTANCE),
      createConfiguration("All matches without guard", "matchwithoutguard",
        """'_expr match {
          |  case '_pattern* if '_guard{0,0} => '_res
          |}""".stripMargin,
        exprFolder, ScalaFileType.INSTANCE),
      createConfiguration("All match case clauses", "matchwithoutguard",
        """'_expr match {
          |  case 'pattern if '_guard{0,1} => '_res
          |}""".stripMargin,
        exprFolder, ScalaFileType.INSTANCE),
      createConfiguration("Function calls", "matchwithoutguard",
        "'_obj{0,1}.'_func('_para*)",
        exprFolder, ScalaFileType.INSTANCE),
      createConfiguration("Try-catch blocks", "trycatch",
        """try {
          |  '_try*
          |} catch {
          |  case '_exc\: '_excType => '_handler
          |}""".stripMargin,
        exprFolder, ScalaFileType.INSTANCE),
      createConfiguration("Case clauses of try-catch blocks", "trycatchclauses",
        """try {
          |  '_try*
          |} catch {
          |  case 'exc\: '_excType => '_handler
          |}""".stripMargin,
        exprFolder, ScalaFileType.INSTANCE),
      createConfiguration("Try-finally blocks", "tryfinally",
        """try {
          |  '_try*
          |} finally {
          |  '_finally*
          |}""".stripMargin,
        exprFolder, ScalaFileType.INSTANCE),
    ).toArray
  }

  val classFolder = "Scala/Classes"
  val funcFolder = "Scala/Functions"
  val exprFolder = "Scala/Expressions"
}
