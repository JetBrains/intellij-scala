package org.example

/**
 *  - [[org.example.Example.fooInClass]]    ## line: 24, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 *  - [[org.example.Example.fooInObject]]   ## line: 25, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 *  - [[org.example.Example$.fooInClass]]   ## resolved: false ##
 *  - [[org.example.Example$.fooInObject]]  ## line: 25, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 *
 *  - [[org.example.MyClassWithCompanion.fooInClass]]     ## line: 27, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 *  - [[org.example.MyClassWithCompanion.fooInObject]]    ## line: 28, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 *  - [[org.example.MyClassWithCompanion$.fooInClass]]    ## resolved: false ##
 *  - [[org.example.MyClassWithCompanion$.fooInObject]]   ## line: 28, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 *
 *  - [[org.example.MyClassWithoutCompanion.fooInClass]]    ## line: 30, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 *  - [[org.example.MyClassWithoutCompanion.fooInObject]]   ## resolved: false ##
 *  - [[org.example.MyClassWithoutCompanion$.fooInClass]]   ## resolved: false ##
 *  - [[org.example.MyClassWithoutCompanion$.fooInObject]]  ## resolved: false ##
 *
 *  - [[org.example.MyObjectWithoutCompanion.fooInClass]]   ## resolved: false ##
 *  - [[org.example.MyObjectWithoutCompanion.fooInObject]]  ## line: 32, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 *  - [[org.example.MyObjectWithoutCompanion$.fooInClass]]  ## resolved: false ##
 *  - [[org.example.MyObjectWithoutCompanion$.fooInObject]] ## line: 32, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition ##
 */
class Example { def fooInClass: String = null }
object Example { def fooInObject: String = null }

class MyClassWithCompanion { def fooInClass: String = null }
object MyClassWithCompanion { def fooInObject: String = null }

class MyClassWithoutCompanion { def fooInClass: String = null }

object MyObjectWithoutCompanion { def fooInObject: String = null }


