package org.jetbrains.plugins.scala.lang.typeInference

import com.intellij.openapi.extensions.Extensions
import org.jetbrains.plugins.scala.lang.psi.impl.base.InterpolatedStringMacroTypeProvider
import org.jetbrains.plugins.scala.lang.typeInference.testInjectors.SCL12987Injector

import scala.annotation.nowarn

class InterpolatedStringMacroTypeProviderTest extends TypeInferenceTestBase {
  protected def doTypeProviderTest(text: String, extension: InterpolatedStringMacroTypeProvider): Unit = {
    val extensionPoint = Extensions.getRootArea
      .getExtensionPoint(InterpolatedStringMacroTypeProvider.EP_NAME): @nowarn("cat=deprecation")
    extensionPoint.registerExtension(extension): @nowarn("cat=deprecation")
    try {
      doTest(text)
    } finally {
      extensionPoint.unregisterExtension(extension): @nowarn("cat=deprecation")
    }
  }

  //test that specified function on StringContext triggers extension
  def testPluginApplication(): Unit = {
    val text =
      s"""
         |implicit class StringToType(val sc: StringContext) extends AnyVal {
         |  def toType(args: Any*): Any = sys.error("Here would be macro code")
         |}
         |val r = toType"2:Long"
         |println(${START}r${END})
         |//Long
       """.stripMargin
      doTypeProviderTest(text, new SCL12987Injector)
  }

  //test that other functions on StringContexts do not trigger extension
  def testPluginIgnoring(): Unit = {
    val text =
      s"""
         |implicit class AnotherMacro(val sc: StringContext) extends AnyVal {
         |  def foo(args: Any*): Any = sys.error("Here would be macro code")
         |}
         |val r = foo"2:Long"
         |println(${START}r${END})
         |//Any
       """.stripMargin
    doTypeProviderTest(text, new SCL12987Injector)
  }
}
