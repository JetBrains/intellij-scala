package org.example

class MyClassWithCompanion { def fooInClass: String = null }
object MyClassWithCompanion { def fooInObject: String = null }

class MyClassWithConstructorWithCompanion(param: String) { def fooInClass: String = null }
object MyClassWithConstructorWithCompanion { def fooInObject: String = null }

/**
 *  - [[org.example.MyClassWithCompanion]]                 ## file: this, offset: 27, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass ||| file: this, offset: 89, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: MyClassWithCompanion ##
 *  - [[org.example.MyClassWithCompanion$]]                ## file: this, offset: 89, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: MyClassWithCompanion ##
 *
 *  - [[org.example.MyClassWithConstructorWithCompanion]]  ## file: this, offset: 152, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass ||| file: this, offset: 244, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: MyClassWithConstructorWithCompanion ##
 *  - [[org.example.MyClassWithConstructorWithCompanion$]] ## file: this, offset: 244, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: MyClassWithConstructorWithCompanion ##
 *
 *  - [[org.example.Example]]                              ## file: this, offset: 1791, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass |||  file: this, offset: 1806, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: Example ##
 *  - [[org.example.Example$]]                             ## file: this, offset: 1806, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: Example  ##
 */
class Example
object Example

