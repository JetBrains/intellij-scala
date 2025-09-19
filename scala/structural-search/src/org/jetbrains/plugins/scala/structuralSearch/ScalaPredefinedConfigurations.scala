package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.structuralsearch.PredefinedConfigurationUtil.createConfiguration
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration
import com.intellij.structuralsearch.plugin.ui.Configuration
import org.jetbrains.annotations.{Nls, NonNls}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.{LanguageFileTypeBase, Scala3Language}

import javax.swing.Icon

object ScalaPredefinedConfigurations {
  object Scala3FileType extends LanguageFileTypeBase(Scala3Language.INSTANCE) {
    def getExtensionWithDot: String = "." + getDefaultExtension
    override def getIcon: Icon = Icons.SCALA_FILE
  }
  
  def createPredefinedTemplated(): Array[Configuration] = {
    Seq(
      createConfiguration(ScalaStructuralSearchBundle.message("any.class"), "classany",
        "class '_name { }",
        classFolder, Scala3FileType),
      createConfiguration(ScalaStructuralSearchBundle.message("class.with.selected.primary.constructor"), "classprimary",
        "class '_name(var '_arg1\\: '_ty, val '_arg2\\: '_ty2) { }",
        classFolder, Scala3FileType),
      createConfiguration(ScalaStructuralSearchBundle.message("class.with.parent.classes"), "classparent",
        "class '_name extends '_parent1, '_parent2 { }",
        classFolder, Scala3FileType),
      createConfiguration(ScalaStructuralSearchBundle.message("class.with.property"), "classprop",
        """class '_class {
          | var '_a\: '_tya
          | var '_b\: '_tyb
          |}""".stripMargin,
        classFolder, Scala3FileType),
      createConfiguration(ScalaStructuralSearchBundle.message("class.with.functions"), "classprop",
        """class '_class {
          | def '_func('_arg*\: '_ty): '_ret = { '_body* }
          |}""".stripMargin,
        classFolder, Scala3FileType),
      createConfiguration(ScalaStructuralSearchBundle.message("all.value.properties.of.a.class"), "classvalprops",
        """class '_class {
          | val 'prop\: '_ty{0,1} = '_expr{0,1}
          |}""".stripMargin,
        classFolder, Scala3FileType),
      createConfiguration(ScalaStructuralSearchBundle.message("all.variable.properties.of.a.class"), "classvalprops",
        """class '_class {
          | var 'prop\: '_ty{0,1} = '_expr{0,1}
          |}""".stripMargin,
        classFolder, Scala3FileType),
      createConfiguration(ScalaStructuralSearchBundle.message("all.functions.of.a.class"), "classvalprops",
        """class '_class {
          | def 'func('_para*\: '_ty{0,1}): '_ret{0,1} = {
          |   '_body*
          | }
          |}""".stripMargin,
        classFolder, Scala3FileType),
      createConfiguration(ScalaStructuralSearchBundle.message("class.without.certain.modifiers"), "classwithoutmod",
        """class '_class:[ script( "!__context__.hasModifierPropertyScala("final")" ) ] {
          |}""".stripMargin,
        classFolder, Scala3FileType),
      createConfiguration(ScalaStructuralSearchBundle.message("any.function"), "functionany",
        """def '_func('_arg*\: '_ty): '_ret = {
          | '_b*
          |}""".stripMargin,
        funcFolder, Scala3FileType),
      createConfiguration(ScalaStructuralSearchBundle.message("functions.without.return.type"), "functionnotret",
        """def '_func('_arg*\: '_ty): '_ret{0,0} = {
          | '_b*
          |}""".stripMargin,
        funcFolder, Scala3FileType),
      createConfiguration(ScalaStructuralSearchBundle.message("functions.with.annotation"), "functionannot",
        """@'_anno
          |def '_func('_arg*\: '_ty): '_ret = {
          | '_b*
          |}""".stripMargin,
        funcFolder, Scala3FileType),
      createConfiguration(ScalaStructuralSearchBundle.message("function.without.certain.modifiers"), "funcwithoutmod",
        """def '_func:[ script( "!__context__.hasModifierPropertyScala("private")" ) ]""".stripMargin,
        funcFolder, Scala3FileType),
      createConfiguration(ScalaStructuralSearchBundle.message("filter.functions.by.type"), "funcbytype",
        """def '_func:[ exprtype( Int => Int ) ]('_par*)""".stripMargin,
        funcFolder, Scala3FileType),
      createConfiguration(ScalaStructuralSearchBundle.message("any.if.else.expression"), "ifany",
        "if ('_cond) '_then*\nelse '_else*\n",
        exprFolder, Scala3FileType),
      createConfiguration(ScalaStructuralSearchBundle.message("any.if.expression"), "ifany",
        "if ('_cond) '_then*\n",
        exprFolder, Scala3FileType),
      createConfiguration(ScalaStructuralSearchBundle.message("any.match.expression"), "matchany",
        """'_expr match {
          |  case '_pattern* => '_res
          |}""".stripMargin,
        exprFolder, Scala3FileType),
      createConfiguration(ScalaStructuralSearchBundle.message("all.matches.without.guard"), "matchwithoutguard",
        """'_expr match {
          |  case '_pattern* if '_guard{0,0} => '_res
          |}""".stripMargin,
        exprFolder, Scala3FileType),
      createConfiguration(ScalaStructuralSearchBundle.message("all.match.case.clauses"), "matchwithoutguard",
        """'_expr match {
          |  case 'pattern if '_guard{0,1} => '_res
          |}""".stripMargin,
        exprFolder, Scala3FileType),
      createConfiguration(ScalaStructuralSearchBundle.message("function.calls"), "matchwithoutguard",
        "'_obj{0,1}.'_func('_para*)",
        exprFolder, Scala3FileType),
      createConfiguration(ScalaStructuralSearchBundle.message("function.calls.filtered.by.type"), "matchwithoutguard",
        "'_obj{0,1}.'_func:[ exprtype( Int => Int ) ]('_para*)",
        exprFolder, Scala3FileType),
      createConfiguration(ScalaStructuralSearchBundle.message("try.catch.blocks"), "trycatch",
        """try {
          |  '_try*
          |} catch {
          |  case '_exc\: '_excType => '_handler
          |}""".stripMargin,
        exprFolder, Scala3FileType),
      createConfiguration(ScalaStructuralSearchBundle.message("case.clauses.of.try.catch.blocks"), "trycatchclauses",
        """try {
          |  '_try*
          |} catch {
          |  case 'exc\: '_excType => '_handler
          |}""".stripMargin,
        exprFolder, Scala3FileType),
      createConfiguration(ScalaStructuralSearchBundle.message("try.finally.blocks"), "tryfinally",
        """try {
          |  '_try*
          |} finally {
          |  '_finally*
          |}""".stripMargin,
        exprFolder, Scala3FileType),
      createSRConfiguration(ScalaStructuralSearchBundle.message("remove.annotations.of.a.function"), "remfuncannot",
        """@'_anno*
          |def '_func('_para*)""".stripMargin,
        """def $func$($para$)""",
        replaceFolder),
      createSRConfiguration(ScalaStructuralSearchBundle.message("remove.extensions.of.a.class"), "remclassext",
        """class '_class extends '_super*""".stripMargin,
        """class $class$""",
        replaceFolder),
      createSRConfiguration(ScalaStructuralSearchBundle.message("convert.value.into.function"), "valtofunc",
        """val '_val""".stripMargin,
        """def $val$()""",
        replaceFolder),
      createSRConfiguration(ScalaStructuralSearchBundle.message("convert.function.into.value"), "functoval",
        """def '_func""".stripMargin,
        """val $func$()""",
        replaceFolder),
      createSRConfiguration(ScalaStructuralSearchBundle.message("convert.var.to.val"), "vartoval",
        """var '_var""".stripMargin,
        """val $var$""",
        replaceFolder),
        createSRConfiguration(ScalaStructuralSearchBundle.message("convert.val.to.var"), "valtovar",
          """val '_val""".stripMargin,
          """var $val$""",
        replaceFolder),
    ).toArray
  }

  val classFolder = "Scala/Classes"
  val funcFolder = "Scala/Functions"
  val exprFolder = "Scala/Expressions"
  val replaceFolder = "Scala/Replace Examples"

  def createSRConfiguration(@Nls(capitalization = Nls.Capitalization.Sentence) name: String,
                            @NonNls refName: String,
                            @NonNls criteria: String,
                            rtemplate: String,
                            category: String): Configuration = {
    val config = new ReplaceConfiguration(name, category)
    config.setPredefined(true)
    config.setRefName(refName)

    val soptions = config.getMatchOptions
    soptions.fillSearchCriteria(criteria)
    soptions.setFileType(Scala3FileType)
    soptions.setCaseSensitiveMatch(true)
    soptions.setPatternContext(null)

    val roptions = config.getReplaceOptions
    roptions.setReplacement(rtemplate)
    config
  }
}
