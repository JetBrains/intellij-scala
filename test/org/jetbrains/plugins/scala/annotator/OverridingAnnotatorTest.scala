package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.extensions._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScVariable, ScTypeAlias, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.01.12
 */

class OverridingAnnotatorTest extends SimpleTestCase {
  final val Header = "\n"
  
  def testPrivateVal() {
    assertMatches(messages(
      """
object ppp {
class Base {
  private val something = 5
}

class Derived extends Base {
  private val something = 8
}
}
      """)) {
      case Nil =>
    }
  }

  def testClassParameter() {
    assertMatches(messages(
      """
object ppp {
class A(x: Int)
class B(val x: Int) extends A(x)
case class C(x: Int) extends A(x)
}
      """)) {
      case Nil =>
    }
  }

  def testVal() {
    assertMatches(messages(
      """
object ppp {
class Base {
  val something = 5
}

class Derived extends Base {
  val something = 8
}
}
      """)) {
      case List(Error(something, "Value 'something' needs override modifier")) =>
    }
  }

  def messages(code: String): List[Message] = {
    val annotator = new OverridingAnnotator() {}
    val mock = new AnnotatorHolderMock

    val element: PsiElement = (Header + code).parse

    val visitor = new ScalaRecursiveElementVisitor {
      override def visitFunction(fun: ScFunction) {
        if (fun.getParent.isInstanceOf[ScTemplateBody]) {
          annotator.checkOverrideMethods(fun, mock)
        }
        super.visitFunction(fun)
      }

      override def visitTypeDefintion(typedef: ScTypeDefinition) {
        if (typedef.getParent.isInstanceOf[ScTemplateBody]) {
          annotator.checkOverrideTypes(typedef, mock)
        }
        super.visitTypeDefintion(typedef)
      }

      override def visitTypeAlias(alias: ScTypeAlias) {
        if (alias.getParent.isInstanceOf[ScTemplateBody]) {
          annotator.checkOverrideTypes(alias, mock)
        }
        super.visitTypeAlias(alias)
      }

      override def visitVariable(varr: ScVariable) {
        if (varr.getParent.isInstanceOf[ScTemplateBody] ||
          varr.getParent.isInstanceOf[ScEarlyDefinitions]) {
          annotator.checkOverrideVars(varr, mock)
        }
        super.visitVariable(varr)
      }

      override def visitValue(v: ScValue) {
        if (v.getParent.isInstanceOf[ScTemplateBody] ||
          v.getParent.isInstanceOf[ScEarlyDefinitions]) {
          annotator.checkOverrideVals(v, mock)
        }
        super.visitValue(v)
      }

      override def visitClassParameter(parameter: ScClassParameter) {
        annotator.checkOverrideClassParameters(parameter, mock)
        super.visitClassParameter(parameter)
      }
    }

    element.accept(visitor)

    mock.annotations
  }
}
