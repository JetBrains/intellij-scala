package org.example

/**
 *  - [[Example]]                              ## line: 19, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass ||| line: 20, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: Example ##
 *  - [[Example$]]                             ## line: 20, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: Example ##
 *
 *  - [[MyClassWithCompanion]]                 ## line: 22, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass ||| line: 23, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: MyClassWithCompanion
 *  - [[MyClassWithCompanion$]]                ## line: 23, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: MyClassWithCompanion ##
 *
 *  - [[MyClassWithConstructorWithCompanion]]  ## line: 25, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass ||| line: 26, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: MyClassWithConstructorWithCompanion ##
 *  - [[MyClassWithConstructorWithCompanion$]] ## line: 26, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: MyClassWithConstructorWithCompanion ##
 *
 *  - [[MyClassWithoutCompanion]]              ## line: 28, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass ##
 *  - [[MyObjectWithoutCompanion]]             ## line: 30, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject ##
 *
 *  - [[MyClassWithoutCompanion$]]             ## resolved: false ##
 *  - [[MyObjectWithoutCompanion$]]            ## line: 30, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: MyObjectWithoutCompanion ##
 */
class Example
object Example

class MyClassWithCompanion
object MyClassWithCompanion

class MyClassWithConstructorWithCompanion(param: String)
object MyClassWithConstructorWithCompanion

class MyClassWithoutCompanion

object MyObjectWithoutCompanion


