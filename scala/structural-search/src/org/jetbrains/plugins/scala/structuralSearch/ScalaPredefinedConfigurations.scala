package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.structuralsearch.PredefinedConfigurationUtil.createConfiguration
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration
import com.intellij.structuralsearch.plugin.ui.Configuration
import org.jetbrains.annotations.{Nls, NonNls}
import org.jetbrains.plugins.scala.{Scala3Language, ScalaFileType}

object ScalaPredefinedConfigurations {
  
  def createPredefinedTemplated(): Array[Configuration] = {
    Seq(
      createSConfiguration(ScalaStructuralSearchBundle.message("any.class"), "classany",
        "class '_name { }",
        classFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("class.with.selected.primary.constructor"), "classprimary",
        "class '_name(var '_arg1\\: '_ty, val '_arg2\\: '_ty2) { }",
        classFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("class.with.parent.classes"), "classparent",
        "class '_name extends '_parent1, '_parent2 { }",
        classFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("class.with.property"), "classprop",
        """class '_class {
          | var '_a\: '_tya
          | var '_b\: '_tyb
          |}""".stripMargin,
        classFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("class.with.functions"), "classprop",
        """class '_class {
          | def '_func('_arg*\: '_ty{0,1})
          |}""".stripMargin,
        classFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("all.value.properties.of.a.class"), "classvalprops",
        """class '_class {
          | val 'prop\: '_ty{0,1} = '_expr{0,1}
          |}""".stripMargin,
        classFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("all.variable.properties.of.a.class"), "classvalprops",
        """class '_class {
          | var 'prop\: '_ty{0,1} = '_expr{0,1}
          |}""".stripMargin,
        classFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("all.functions.of.a.class"), "classvalprops",
        """class '_class {
          | def 'func('_para*)
          |}""".stripMargin,
        classFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("class.without.certain.modifiers"), "classwithoutmod",
        """class '_class:[ script( "!__context__.hasModifierPropertyScala("final")" ) ] {
          |}""".stripMargin,
        classFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("any.function"), "functionany",
        """def '_func('_arg*)""",
        funcFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("any.function.full.template"), "functionanyfull",
        """def '_func('_arg*\: '_ty{0,1}): '_ret{0,1} = {
          | '_stmt*
          |}""".stripMargin,
        funcFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("functions.without.return.type"), "functionnotret",
        """def '_func('_arg*)\: '_ret{0,0} """,
        funcFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("functions.with.annotation"), "functionannot",
        """@'_anno
          |def '_func('_arg*)""".stripMargin,
        funcFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("function.without.certain.modifiers"), "funcwithoutmod",
        """def '_func:[ script( "!__context__.hasModifierPropertyScala("private")" ) ]('_arg*)""".stripMargin,
        funcFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("filter.functions.by.type"), "funcbytype",
        """def '_func:[ exprtype( Int => Int ) ]('_par*)""".stripMargin,
        funcFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("any.if.else.expression"), "ifany",
        "if ('_cond) '_then*\nelse '_else*\n",
        exprFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("any.if.expression"), "ifany",
        "if ('_cond) '_then*\n",
        exprFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("any.match.expression"), "matchany",
        """'_expr match {
          |  case '_pattern* => '_res
          |}""".stripMargin,
        exprFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("all.matches.without.guard"), "matchwithoutguard",
        """'_expr match {
          |  case '_pattern{1,} if '_guard{0,0} => '_res
          |}""".stripMargin,
        exprFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("all.match.case.clauses"), "matchwithoutguard",
        """'_expr match {
          |  case 'pattern{1,} if '_guard{0,1} => '_res
          |}""".stripMargin,
        exprFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("function.calls"), "matchwithoutguard",
        "'_obj{0,1}.'_func('_para*)",
        exprFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("function.calls.filtered.by.type"), "matchwithoutguard",
        "'_obj{0,1}.'_func:[ exprtype( Int => Int ) ]('_para*)",
        exprFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("try.catch.blocks"), "trycatch",
        """try {
          |  '_try*
          |} catch {
          |  case '_exc{1,}\: '_excType => '_handler
          |}""".stripMargin,
        exprFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("case.clauses.of.try.catch.blocks"), "trycatchclauses",
        """try {
          |  '_try*
          |} catch {
          |  case 'exc{1,}\: '_excType => '_handler
          |}""".stripMargin,
        exprFolder),
      createSConfiguration(ScalaStructuralSearchBundle.message("try.finally.blocks"), "tryfinally",
        """try {
          |  '_try*
          |} finally {
          |  '_finally*
          |}""".stripMargin,
        exprFolder),
      createSRConfiguration(ScalaStructuralSearchBundle.message("remove.annotations.of.a.function"), "remfuncannot",
        """@'_anno*
          |def '_func('_para*)""".stripMargin,
        """def $func$($para$)""",
        replaceFolder),
      createSRConfiguration(ScalaStructuralSearchBundle.message("strip.parent.types.of.a.class"), "remclassext",
        """class '_class extends '_super*""".stripMargin,
        """class $class$""",
        replaceFolder),
      createSRConfiguration(ScalaStructuralSearchBundle.message("convert.value.into.function"), "valtofunc",
        """val '_val""".stripMargin,
        """def $val$()""",
        replaceFolder),
      createSRConfiguration(ScalaStructuralSearchBundle.message("convert.function.into.value"), "functoval",
        """def '_func""".stripMargin,
        """val $func$""",
        replaceFolder),
      createSRConfiguration(ScalaStructuralSearchBundle.message("convert.var.to.val"), "vartoval",
        """var '_var""".stripMargin,
        """val $var$""",
        replaceFolder),
        createSRConfiguration(ScalaStructuralSearchBundle.message("convert.val.to.var"), "valtovar",
          """val '_val""".stripMargin,
          """var $val$""",
        replaceFolder),
      createSRConfiguration(ScalaStructuralSearchBundle.message("add.deprecated.annotation.to.function"), "addannotfunc",
        """@'_depre{0,1}:[ regex( deprecated ) ] @'_anno*
          |def '_func('_para*)""".stripMargin,
        """@deprecated @$anno$
          |def $func$($para$)""".stripMargin,
        replaceFolder),
      createSRConfiguration(ScalaStructuralSearchBundle.message("convert.properties.to.constructor.arguments"), "proptoconstr",
        """class '_cl('_para*) {
          |  val '_val*
          |  var '_var*
          |}""".stripMargin,
        """class $cl$($para$, $val$, $var$) {
          |}""".stripMargin,
        replaceFolder),
    ).toArray
  }

  val classFolder = "Scala/Classes"
  val funcFolder = "Scala/Functions"
  val exprFolder = "Scala/Expressions"
  val replaceFolder = "Scala/Replace Examples"

  def createSConfiguration(@Nls name: String, refName: String, criteria: String, category: String): Configuration = {
    val config = createConfiguration(name, refName, criteria, category, ScalaFileType.INSTANCE)
    config.getMatchOptions.setDialect(Scala3Language.INSTANCE)
    config
  }
  
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
    soptions.setFileType(ScalaFileType.INSTANCE)
    soptions.setDialect(Scala3Language.INSTANCE)
    soptions.setCaseSensitiveMatch(true)
    soptions.setPatternContext(null)

    val roptions = config.getReplaceOptions
    roptions.setReplacement(rtemplate)
    config
  }
}
