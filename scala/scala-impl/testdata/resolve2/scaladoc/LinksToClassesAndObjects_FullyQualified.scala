package org.example

/**
 *  - [[org.example.Example]]                              ## line: 19, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass ||| line: 20, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: Example ##
 *  - [[org.example.Example$]]                             ## line: 20, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: Example ##
 *
 *  - [[org.example.MyClassWithCompanion]]                 ## line: 22, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass ||| line: 23, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: MyClassWithCompanion
 *  - [[org.example.MyClassWithCompanion$]]                ## line: 23, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: MyClassWithCompanion ##
 *
 *  - [[org.example.MyClassWithConstructorWithCompanion]]  ## line: 25, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass ||| line: 26, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: MyClassWithConstructorWithCompanion ##
 *  - [[org.example.MyClassWithConstructorWithCompanion$]] ## line: 26, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: MyClassWithConstructorWithCompanion ##
 *
 *  - [[org.example.MyClassWithoutCompanion]]              ## line: 28, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass ##
 *  - [[org.example.MyObjectWithoutCompanion]]             ## line: 30, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject ##
 *
 *  - [[org.example.MyClassWithoutCompanion$]]             ## resolved: false ##
 *  - [[org.example.MyObjectWithoutCompanion$]]            ## line: 30, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: MyObjectWithoutCompanion ##
 */
class Example
object Example

class MyClassWithCompanion
object MyClassWithCompanion

class MyClassWithConstructorWithCompanion(param: String)
object MyClassWithConstructorWithCompanion

class MyClassWithoutCompanion

object MyObjectWithoutCompanion


