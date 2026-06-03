/**
 *  - [[myMethodInClass]]   ## line: 7, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 *  - [[myMethodCommon]]    ## line: 8, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 *  - [[myMethodInObject]]  ## resolved: false
 */
class ScalaDocLinkToMemberInSameClass {
  def myMethodInClass: Int = 0
  def myMethodCommon: Int = 0
}

/**
 *  - [[myMethodInObject]] ## line: 17, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 *  - [[myMethodCommon]]   ## line: 18, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 *  - [[myMethodInClass]]  ## resolved: false
 */
object ScalaDocLinkToMemberInSameClass {
  def myMethodInObject: Int = 0
  def myMethodCommon: Int = 0
}


/**
 *  - [[myMethodInTrait]]            ## line: 27, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 *  - [[myMethodInTraitOverloaded]]  ## line: 28, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ||| line: 29, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 */
object ScalaDocLinkToMemberInSameTrait {
  def myMethodInTrait: Int = 0
  def myMethodInTraitOverloaded: Int = 0
  def myMethodInTraitOverloaded(x: Int): Int = 0
}
