package org.jetbrains.plugins.scala
package lang.lexer

import com.intellij.psi.xml.XmlTokenType
import junit.framework.TestCase
import org.junit.Assert._
import org.junit.experimental.categories.Category

@Category(Array(classOf[LanguageTests]))
class ScalaXmlTokenTypesTest extends TestCase {

  def testXmlTokenTypesExistInPlatformAnalog(): Unit = {
    val fields = org.jetbrains.plugins.scala.lang.lexer.ScalaXmlTokenTypes.getClass.getFields
    val tokenFields = fields.filter(_.getType == classOf[ScalaXmlLexer.ScalaXmlTokenType])

    val classFromPlatform = classOf[XmlTokenType]
    tokenFields.foreach { field =>
      try {
        classFromPlatform.getField(field.getName)
      } catch {
        case _: NoSuchFieldException =>
          fail(s"no field ${field.getName} found in class $classFromPlatform")
      }
    }
  }
}