package org.example

class MyClassWithCompanion { def fooInClass: String = null }
object MyClassWithCompanion { def fooInObject: String = null }

class MyClassWithConstructorWithCompanion(param: String) { def fooInClass: String = null }
object MyClassWithConstructorWithCompanion { def fooInObject: String = null }

/**
 *  - [[MyClassWithCompanion]]                 ## file: this, offset: 27, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass ||| file: this, offset: 89, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: MyClassWithCompanion ##
 *  - [[MyClassWithCompanion$]]                ## file: this, offset: 89, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: MyClassWithCompanion ##
 *
 *  - [[MyClassWithConstructorWithCompanion]]  ## file: this, offset: 152, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass ||| file: this, offset: 244, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject ##
 *  - [[MyClassWithConstructorWithCompanion$]] ## file: this, offset: 244, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: MyClassWithConstructorWithCompanion ##
 *
 *  - [[Example]]                              ## file: this, offset: 1675, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass |||  file: this, offset: 1690, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: Example ##
 *  - [[Example$]]                             ## file: this, offset: 1690, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: Example ##
 */
class Example
object Example

