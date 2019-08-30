package org.jetbrains.plugins.scala.lang.parser

/*
 * GivenDef         ::=  [id] [DefTypeParamClause] GivenBody
 * GivenBody        ::=  [‘as’ ConstrApp {‘,’ ConstrApp }] {GivenParamClause} [TemplateBody]
 *                    |  ‘as’ Type {GivenParamClause} ‘=’ Expr
 *                    |  ‘(’ DefParam ‘)’ TemplateBody
 */
class GivenInstanceParserTest extends SimpleParserTestBase {

  def test_combinations(): Unit = {
    val givenClauses = Seq("", "given (a: Ty)", "given Int", "given T1, given T2")
    val constrApps = Seq("", "Ty", "Ty(args)", "Ty1(args), Ty2(more, args)")

    val collectiveGiven = Seq("(param: Ty) {}")
    val givenExpr = for (givenClause <- givenClauses)
                      yield s"as GTy $givenClause = ()"
    val givenDefinition = for (constrApp <- constrApps; givenClause <- givenClauses)
                            yield if (constrApp == "") givenClause + " {}"
                                  else s"as $constrApp $givenClause {}"

    val givenBody = collectiveGiven ++ givenExpr ++ givenDefinition

    val givenDef = for {
      body <- givenBody
      typeParams <- Seq("", "[T]")
      id <- Seq("", "someId", "`as`", "`given`")
    } yield s"given $id $typeParams $body"

    for (cur <- givenDef) {
      println("try parse: " + cur)
      checkParseErrors(cur)
    }
  }
}
