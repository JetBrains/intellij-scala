package org.example

/**
 *  - [[Example.fooInClass]]    ## line: 24, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 *  - [[Example.fooInObject]]   ## line: 25, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 *  - [[Example$.fooInClass]]   ## resolved: false ##
 *  - [[Example$.fooInObject]]  ## line: 25, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 *
 *  - [[MyClassWithCompanion.fooInClass]]     ## line: 27, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 *  - [[MyClassWithCompanion.fooInObject]]    ## line: 28, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 *  - [[MyClassWithCompanion$.fooInClass]]    ## resolved: false ##
 *  - [[MyClassWithCompanion$.fooInObject]]   ## line: 28, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 *
 *  - [[MyClassWithoutCompanion.fooInClass]]    ## line: 30, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 *  - [[MyClassWithoutCompanion.fooInObject]]   ## resolved: false ##
 *  - [[MyClassWithoutCompanion$.fooInClass]]   ## resolved: false ##
 *  - [[MyClassWithoutCompanion$.fooInObject]]  ## resolved: false ##
 *
 *  - [[MyObjectWithoutCompanion.fooInClass]]   ## resolved: false ##
 *  - [[MyObjectWithoutCompanion.fooInObject]]  ## line: 32, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 *  - [[MyObjectWithoutCompanion$.fooInClass]]  ## resolved: false ##
 *  - [[MyObjectWithoutCompanion$.fooInObject]] ## line: 32, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 */
class Example { def fooInClass: String = null }
object Example { def fooInObject: String = null }

class MyClassWithCompanion { def fooInClass: String = null }
object MyClassWithCompanion { def fooInObject: String = null }

class MyClassWithoutCompanion { def fooInClass: String = null }

object MyObjectWithoutCompanion { def fooInObject: String = null }


