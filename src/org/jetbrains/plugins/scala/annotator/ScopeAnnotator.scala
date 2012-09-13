package org.jetbrains.plugins.scala
package annotator

import com.intellij.lang.annotation.AnnotationHolder
import lang.psi.api.toplevel.templates.ScTemplateBody
import lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition, ScClass}
import lang.psi.api.statements._
import params.{ScParameter, ScTypeParam}
import lang.psi.api.toplevel.{ScTypedDefinition, ScNamedElement}
import lang.psi.api.expr.{ScGenerator, ScForStatement, ScBlockExpr}
import org.jetbrains.plugins.scala.extensions._
import com.intellij.psi.PsiElement
import lang.psi.types.ScType
import collection.mutable.ArrayBuffer

/**
 * Pavel.Fatin, 25.05.2010
 */

trait ScopeAnnotator {
  private type Definitions = List[ScNamedElement]
  private val TypeParameters = """\[.*\]""".r

  def annotateScope(element: PsiElement, holder: AnnotationHolder) {
    if (!element.isScope) return
    def checkScope(elements: PsiElement*) {
      val (types, terms, parameters, caseClasses, objects) = definitionsIn(elements : _*)

      val jointTerms = terms ::: parameters

      val complexClashes = clashesOf(jointTerms ::: objects) :::
        clashesOf(types ::: caseClasses) :::
        clashesOf(jointTerms ::: caseClasses)

      val clashes = (complexClashes.distinct diff clashesOf(parameters))

      clashes.foreach {
        e =>
          holder.createErrorAnnotation(e.getNameIdentifier,
            ScalaBundle.message("id.is.already.defined", nameOf(e)))
      }
    }
    element match {
      case f: ScForStatement =>
        f.enumerators.foreach {
          case enumerator =>
            val elements = new ArrayBuffer[PsiElement]()
            enumerator.children.foreach {
              case generator: ScGenerator =>
                checkScope(elements.toSeq: _*)
                elements.clear()
                elements += generator
              case child => elements += child
            }
            checkScope(elements.toSeq: _*)
        }
      case _ => checkScope(element)
    }
  }

  private def definitionsIn(elements: PsiElement*) = {
    var types: Definitions = List()
    var terms: Definitions = List()
    var parameters: Definitions = List()
    var caseClasses: Definitions = List()
    var objects: Definitions = List()
    elements.foreach {
      case element =>
        if(element.isInstanceOf[ScTemplateBody]) element match {
          case Parent(Parent(aClass: ScClass)) => parameters :::= aClass.parameters.toList
          case _ =>
        }

        element.children.foreach {
          _.depthFirst(!_.isScope).foreach {
            case e: ScObject => objects ::= e
            case e: ScFunction => if(e.typeParameters.isEmpty) terms ::= e
            case e: ScTypedDefinition => terms ::= e
            case e: ScTypeAlias => types ::= e
            case e: ScTypeParam => types ::= e
            case e: ScClass if e.isCase => caseClasses ::= e
            case e: ScTypeDefinition => types ::= e
            case _ =>
          }
        }
    }

    (types, terms, parameters, caseClasses, objects)
  }

  private def clashesOf(elements: Definitions): Definitions = {
    val names = elements.map(nameOf _).filterNot(_ == "_")
    val clashedNames = names.diff(names.distinct)
    elements.filter(e => clashedNames.contains(nameOf(e)))
  }

  private def nameOf(element: ScNamedElement): String = element match {
    case f: ScFunction if !f.getParent.isInstanceOf[ScBlockExpr] => f.name + signatureOf(f)
    case _ => element.name
  }

  private def signatureOf(f: ScFunction): String = {
    if(f.parameters.isEmpty)
      ""
    else
      f.paramClauses.clauses.map(clause => format(clause.parameters, clause.paramTypes)).mkString
  }

  private def eraseType(s: String) =
    if (s.startsWith("Array[") || s.startsWith("_root_.scala.Array[")) s
    else TypeParameters.replaceFirstIn(s, "")

  private def format(parameters: Seq[ScParameter], types: Seq[ScType]) = {
    val parts = parameters.zip(types).map {
      case (p, t) => eraseType(t.canonicalText) + (if(p.isRepeatedParameter) "*" else "")
    }
    "(%s)".format(parts.mkString(", "))
  }
}