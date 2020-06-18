package org.jetbrains.plugins.scala.lang.scaladoc.lexer

import java.lang.reflect.Field

import com.intellij.psi.tree.IElementType
import junit.framework.TestCase
import org.jetbrains.plugins.scala.extensions.TraversableExt
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScalaDocSyntaxElementType
import org.junit.Assert._

class ScalaDocTokenTypeTest extends TestCase {

  def testAllScaladocSyntaxElementShouldHaveUniqueFlags(): Unit = {
    val syntaxElements = ScalaDocTokenType.ALL_SCALADOC_TOKENS.getTypes.toSeq.filterBy[ScalaDocSyntaxElementType]
    assertTrue(syntaxElements.nonEmpty)

    val map = syntaxElements.groupBy(_.getFlagConst)
    map.foreach { case (flagValue, elements) =>
      assertTrue(
        s"Some elements have non-unique flag value: $flagValue\n${elements.mkString(", ")}",
        elements.size == 1 ||
          // see VALID_DOC_HEADER's TODO
          elements.toSet == Set(ScalaDocTokenType.VALID_DOC_HEADER, ScalaDocTokenType.DOC_HEADER)
      )
    }
  }

  def testAllScalaDocTokensShouldContainAllTokensIndeed(): Unit = {
    val declaredTokensField = declaredTokenTypesFields
    val nonRegisteredElementsFields = declaredTokensField.filterNot { tokenField =>
      val elementValue = tokenField.value.asInstanceOf[IElementType]
      ScalaDocTokenType.ALL_SCALADOC_TOKENS.contains(elementValue) ||
        elementValue == ScalaDocTokenType.DOC_HTML_ESCAPE_HIGHLIGHTED_ELEMENT ||
        elementValue == ScalaDocTokenType.DOC_COMMENT_BAD_CHARACTER ||
        elementValue == ScalaDocTokenType.DOC_COMMON_CLOSE_WIKI_TAG
    }
    assertTrue(
      s"All token types should be registered, but these are not:\n${nonRegisteredElementsFields.map(_.name).mkString(", ")}",
      nonRegisteredElementsFields.isEmpty
    )
  }
  def testAllScalaDocSyntaxTokensShouldContainAllTokensIndeed(): Unit = {
    val declaredTokensField = declaredTokenTypesFields
    val nonRegisteredElementsFields = declaredTokensField.filterNot { tokenField =>
      val elementValue = tokenField.value.asInstanceOf[ScalaDocSyntaxElementType]
      ScalaDocTokenType.ALL_SCALADOC_SYNTAX_ELEMENTS.contains(elementValue)
    }
    assertTrue(
      s"All syntax token types should be registered, but these are not:\n${nonRegisteredElementsFields.map(_.name).mkString(", ")}",
      nonRegisteredElementsFields.isEmpty
    )
  }

  def testAllScalaDocTokensDebugNameShouldBeEqualToTheFieldName(): Unit = {
    val invalid = declaredTokenTypesFields
      .map(tokenField => (tokenField.name, tokenField.value.toString))
      .filter { case (fieldName, debugName) =>
        debugName != fieldName &&
          !debugName.startsWith(fieldName + " ") // syntax elements with flags, e.g. `DOC_SUBSCRIPT_TAG 32`
      }

    assertTrue(
      s"Tokens debug name is not equal to the field name:\n${invalid.mkString("\n")}",
      invalid.isEmpty
    )
  }

  private def declaredTokenTypesFields: Array[FieldDescriptor] = {
    val fields: Array[Field]  = classOf[ScalaDocTokenType].getDeclaredFields
    fields
      .filter(f => classOf[ScalaDocElementType].isAssignableFrom(f.getType))
      .map(f => FieldDescriptor(f.getName, f.get(null).asInstanceOf[ScalaDocElementType]))
  }

  private case class FieldDescriptor(name: String, value: ScalaDocElementType)
}