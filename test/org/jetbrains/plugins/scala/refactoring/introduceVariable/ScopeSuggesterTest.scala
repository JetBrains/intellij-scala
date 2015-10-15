package org.jetbrains.plugins.scala.refactoring.introduceVariable

/**
 * Created by user 
 * on 10/15/15
 */
class ScopeSuggesterTest extends AbstractScopeSuggesterTest{
  def testOkSimple() =  doTest(Seq("class OkSimple"))

  def testOkTypeAlias() = doTest(Seq("trait B"))

  def testOkTypeAlias2() = doTest(Seq("class OkTypeAlias2", "trait B"))

  def testOkTypeParam() =  doTest(Seq("class Inner"))

  def testNokFunction() = doTest(Seq(""))
}
