package org.jetbrains.plugins.scala.structureView

import com.intellij.lang.Language
import org.jetbrains.plugins.scala.icons.Icons.*
import org.jetbrains.plugins.scala.structureView.ScalaStructureViewTestBase.Node
import org.jetbrains.plugins.scala.{ScalaLanguage, ScalaVersion}

class Scala2StructureViewTest extends ScalaStructureViewCommonTests {

  override protected def scalaLanguage: Language = ScalaLanguage.INSTANCE

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  def testNavigationFromSourceScala2(): Unit = checkNavigationFromSource(
    s"""
       |object Container {
       |  implicit class Str${CARET}Ext(private val ${CARET}s: String) extends AnyVal {
       |    def myExten${CARET}sionMethod: String = ???
       |  }
       |
       |  implicit def myImp${CARET}licitDef: String = ""
       |  protected implicit def myAbstra${CARET}ctImplicitDef: Int
       |}
       |""".stripMargin,
    Node(CLASS, "StrExt(String)"), // implicit class StrExt
    Node(FIELD_VAL, PrivateIcon, "s: String"), // implicit class val param s
    Node(MethodIcon, "myExtensionMethod: String"), // def myExtensionMethod
    Node(MethodIcon, "myImplicitDef: String"), // implicit def myImplicitDef
    Node(AbstractMethodIcon, ProtectedIcon, "myAbstractImplicitDef: Int"), // protected implicit def myAbstractImplicitDef
  )
}
