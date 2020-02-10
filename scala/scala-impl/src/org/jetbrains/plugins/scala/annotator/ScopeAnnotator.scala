package org.jetbrains.plugins.scala
package annotator

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.element.ElementAnnotator
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScRefinement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScFor, ScGenerator}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameterClause, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.psi.types.api.{JavaArrayType, ParameterizedType, StdTypes, TypeParameterType, arrayType}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.collection.mutable.ArrayBuffer

/**
 * Pavel.Fatin, 25.05.2010
 */

object ScopeAnnotator extends ElementAnnotator[ScalaPsiElement] {

  private case class Definitions(types: List[ScNamedElement],
                                 functions: List[ScFunction],
                                 parameterless: List[ScNamedElement],
                                 fieldLike: List[ScNamedElement],
                                 classParameters: List[ScClassParameter])

  def annotate(element: ScalaPsiElement, typeAware: Boolean)
              (implicit holder: ScalaAnnotationHolder): Unit =
    annotateScope(element)

  def annotateScope(element: PsiElement)
                   (implicit holder: ScalaAnnotationHolder): Unit = {
    if (!ScalaPsiUtil.isScope(element)) return

    def checkScope(elements: PsiElement*): Unit = {
      val Definitions(types, functions, parameterless, fieldLikes, classParameters) = definitionsIn(elements: _*)

      val clashes =
        clashesOf(functions) :::
        clashesOf(parameterless) :::
        clashesOf(types) :::
        clashesOf(fieldLikes) ::: Nil

      //clashed class parameters were already highlighted
      val withoutClassParameters = clashes.toSet -- clashesOf(classParameters)

      withoutClassParameters.foreach {
        e =>
          holder.createErrorAnnotation(e.getNameIdentifier,
            ScalaBundle.message("id.is.already.defined", nameOf(e)))
      }
    }

    element match {
      case f: ScFor =>
        f.enumerators.foreach {
          enumerator =>
            val elements = new ArrayBuffer[PsiElement]()
            enumerator.children.foreach {
              case generator: ScGenerator =>
                checkScope(elements: _*)
                elements.clear()
                elements += generator
              case child => elements += child
            }
            checkScope(elements: _*)
        }
      case _ => checkScope(element)
    }
  }

  private def definitionsIn(elements: PsiElement*) = {
    var types: List[ScNamedElement] = Nil
    var functions: List[ScFunction] = Nil
    var parameterless: List[ScNamedElement] = Nil
    var fieldLike: List[ScNamedElement] = Nil
    var classParameters: List[ScClassParameter] = Nil
    elements.foreach { element =>

      val classParams = containingClassParams(element)
      classParameters :::= classParams
      fieldLike     :::= classParams.filter(_.isPrivateThis)
      parameterless :::= classParams.filterNot(_.isPrivateThis)

      element.children.foreach {
        _.depthFirst(!ScalaPsiUtil.isScope(_)).foreach {
          case e: ScObject =>
            parameterless ::= e
            fieldLike ::= e
          case e: ScFunction  =>
            if (e.typeParameters.isEmpty) { //generic functions are not supported
              functions ::= e
              if (e.parameters.isEmpty || e.getContext.isInstanceOf[ScBlockExpr]) {
                parameterless ::= e
              }
            }
          case e: ScTypedDefinition =>
            if (generatesFunction(e)) {
              parameterless ::= e
            }
            fieldLike ::= e
          case e: ScTypeAlias => types ::= e
          case e: ScTypeParam => types ::= e
          case e: ScClass  =>
            if (e.isCase && e.baseCompanionModule.isEmpty) { //add synthtetic companion
              parameterless ::= e
              fieldLike ::= e
            }
            types ::= e
          case e: ScTypeDefinition => types ::= e
          case _ =>
        }
      }
    }

    Definitions(types, functions, parameterless, fieldLike, classParameters)
  }

  private def containingClassParams(scope: PsiElement): List[ScClassParameter] = scope match {
    case (_: ScTemplateBody) && Parent(Parent(aClass: ScClass)) =>
      aClass.parameters.toList
    case _ => Nil
  }

  private def generatesFunction(td: ScTypedDefinition): Boolean = td.nameContext match {
    case f: ScFunction => true
    case v: ScValueOrVariable =>
      v.getModifierList.accessModifier match {
        case Some(am) => !(am.isPrivate && am.isThis)
        case None     => true
      }
    case _ => false
  }

  private def clashesOf(elements: List[ScNamedElement]): List[ScNamedElement] = {
    val names = elements.map(nameOf).filterNot(_ == "_")
    val clashedNames = names.diff(names.distinct)
    elements.filter(e => clashedNames.contains(nameOf(e)))
  }

  private def nameOf(element: ScNamedElement): String = element match {
    case f: ScFunction if !f.getParent.isInstanceOf[ScBlockExpr] =>
      ScalaNamesUtil.clean(f.name) + signatureOf(f)
    case _ =>
      ScalaNamesUtil.clean(element.name)
  }

  private def signatureOf(f: ScFunction): String = {
    if (f.parameters.isEmpty)
      ""
    else {
      val isInStructuralType = f.getContext.isInstanceOf[ScRefinement]
      val params = f.paramClauses.clauses.map(format(_, isInStructuralType)).mkString
      val returnType =
        if (!isInStructuralType) erased(f.returnType.getOrAny.removeAliasDefinitions()).canonicalText
        else ""
      params + returnType
    }
  }

  private def erased(t: ScType): ScType = {
    val stdTypes = StdTypes.instance(t.projectContext)

    t.updateRecursively {
      //during erasure literal types collapse into widened types
      case lit: ScLiteralType => lit.wideType
      case ScProjectionType(_, element: Typeable) => element.`type`().map {
        case literalType: ScLiteralType => literalType.widen
        case other => other
      }.getOrAny

      case arrayType(inner) => JavaArrayType(erased(inner)) //array types are not erased
      case p: ParameterizedType => p.designator
      case tpt: TypeParameterType => tpt.upperType
      case stdTypes.Any | stdTypes.AnyVal => stdTypes.AnyRef
    }
  }

  private def format(clause: ScParameterClause, isInStructuralType: Boolean) = {
    val parts = clause.parameters.map { p =>
      val `=>` = if (p.isCallByNameParameter) " => " else ""
      val `*` = if (p.isRepeatedParameter) "*" else ""

      val paramType = p.`type`().getOrAny.removeAliasDefinitions()
      val erasedType =
        if (!isInStructuralType) erased(paramType)
        else paramType

      `=>` + erasedType.canonicalText + `*`
    }
    parts.mkString("(", ", ", ")")
  }
}