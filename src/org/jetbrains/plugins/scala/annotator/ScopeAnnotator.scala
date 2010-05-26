package org.jetbrains.plugins.scala
package annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import lang.psi.api.toplevel.templates.ScTemplateBody
import lang.psi.api.toplevel.packaging.ScPackageContainer
import lang.psi.api.expr.{ScBlock}
import com.intellij.psi.{PsiElement}
import lang.psi.api.toplevel.{ScTypedDefinition, ScNamedElement}
import lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition, ScClass}
import lang.psi.api.statements._
import lang.psi.types.ScType
import params.{ScTypeParam, ScTypeParamClause, ScParameters}
import lang.psi.api.base.patterns.ScCaseClause

/**
 * Pavel.Fatin, 25.05.2010
 */

trait ScopeAnnotator {
  private type Definitions = List[ScNamedElement]

  def annotateScope(element: PsiElement, holder: AnnotationHolder) {
    if (!isScope(element)) return

    val (types, terms, caseClasses, objects) = definitionsIn(element)

    val clashes = (clashesOf(terms ::: objects) :::
            clashesOf(types ::: caseClasses) :::
            clashesOf(terms ::: caseClasses))

    clashes.distinct.foreach { e =>
      holder.createErrorAnnotation(e.getNameIdentifier,
        ScalaBundle.message("id.is.already.defined", nameOf(e)))
    }
  }

  private def definitionsIn(element: PsiElement) = {
    var types: Definitions = List()
    var terms: Definitions = List()
    var caseClasses: Definitions = List()
    var objects: Definitions = List()

    element.children.foreach {
      _.depthFirst(!isScope(_)).foreach {
        case e: ScObject => objects ::= e
        case e: ScTypedDefinition => terms ::= e
        case e: ScTypeAlias => types ::= e
        case e: ScTypeParam => types ::= e
        case e: ScClass if e.isCase => caseClasses ::= e
        case e: ScTypeDefinition => types ::= e
//        case e: ScNamedElement => {types ::= e; terms ::= e}
        case _ =>
      }
    }
    (types, terms, caseClasses, objects)
  }

  private def clashesOf(elements: Definitions): Definitions = {
    val names = elements.map(nameOf _)
    val clashedNames = names.diff(names.distinct)
    elements.filter(e => clashedNames.contains(nameOf(e)))
  }

  def nameOf(element: ScNamedElement): String = element match {
    case f: ScFunction if !f.parameters.isEmpty => {
      def format(types: Seq[ScType]) = "(" + types.map(_.presentableText).mkString(", ") + ")"
      f.getName + f.paramClauses.clauses.map(clause => format(clause.paramTypes)).mkString
    }
    case _ => element.getName
  }

  private def isScope(e: PsiElement): Boolean = {
    e.isInstanceOf[ScalaFile] ||
            e.isInstanceOf[ScBlock] ||
            e.isInstanceOf[ScTemplateBody] ||
            e.isInstanceOf[ScPackageContainer] ||
            e.isInstanceOf[ScParameters] ||
            e.isInstanceOf[ScTypeParamClause] ||
            e.isInstanceOf[ScCaseClause]
  }
}