package org.jetbrains.plugins.scala.lang.psi.stubs.index

import api.statements.{ScFunction, ScValue, ScVariable}
import api.toplevel.packaging.ScPackageContainer
import com.intellij.psi.PsiClass
import com.intellij.psi.stubs.StubIndexKey

/**
 * @author ilyas
 */

object ScalaIndexKeys {

  val SHORT_NAME_KEY: StubIndexKey[String, PsiClass]  = StubIndexKey.createIndexKey("sc.class.shortName")
  val FQN_KEY: StubIndexKey[java.lang.Integer, PsiClass]  = StubIndexKey.createIndexKey("sc.class.fqn")
  val PACKAGE_FQN_KEY: StubIndexKey[java.lang.Integer, ScPackageContainer]  = StubIndexKey.createIndexKey("sc.package.fqn")
  val METHOD_NAME_KEY: StubIndexKey[String, ScFunction] = StubIndexKey.createIndexKey("sc.method.name")
  val VALUE_NAME_KEY: StubIndexKey[String, ScValue] = StubIndexKey.createIndexKey("sc.field.name")
  //val VARIABLE_NAME_KEY: StubIndexKey[String, ScVariable] = StubIndexKey.createIndexKey("sc.field.name")

}