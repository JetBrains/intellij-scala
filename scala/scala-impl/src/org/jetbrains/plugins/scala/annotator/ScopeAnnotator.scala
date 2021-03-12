package org.jetbrains.plugins.scala
package annotator

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.element.ElementAnnotator
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScRefinement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScFor, ScGenerator}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameterClause, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.psi.types.api.{JavaArrayType, ParameterizedType, StdTypes, TypeParameterType, arrayType}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.collection.immutable.ArraySeq
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

  override def annotate(element: ScalaPsiElement, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit =
    annotateScope(element)

  def annotateScope(element: PsiElement)
                   (implicit holder: ScalaAnnotationHolder): Unit = {
    if (!ScalaPsiUtil.isScope(element)) return

    def checkScope(elements: Iterable[PsiElement]): Unit = {
      val Definitions(types, functions, parameterless, fieldLikes, classParameters) = definitionsIn(elements)

      val clashes =
        clashesOf(functions) ++
        clashesOf(parameterless) ++
        clashesOf(types) ++
        clashesOf(fieldLikes)

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
                checkScope(elements)
                elements.clear()
                elements += generator
              case child => elements += child
            }
            checkScope(elements)
        }
      case _ => checkScope(List(element))
    }
  }

  private def definitionsIn(elements: Iterable[PsiElement]) = {
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
              if (e.parameters.isEmpty || e.getContext.is[ScBlockExpr]) {
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
            if (e.isCase && e. baseCompanion.isEmpty) { //add synthtetic companion
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
    case _: ScFunction => true
    case v: ScValueOrVariable =>
      v.getModifierList.accessModifier match {
        case Some(am) => !(am.isPrivate && am.isThis)
        case None     => true
      }
    case _ => false
  }

  // For the exact rules see:
  // https://www.scala-lang.org/files/archive/spec/2.13/05-classes-and-objects.html#class-members
  //
  // We find clashes in three steps
  //  1. we group together all elements that have the same name and the same erasure parameter types.
  //  2. All elements that end up in the same group and are not functions with parameters are already clashes.
  //     The functions are now grouped by their erasure return type.
  //     Functions in the same group have the exact same erasure signature and must therefore be clashes.
  //  3. The remaining functions will not clash if they do not have equivalent parameter types.
  private def clashesOf(elements: Seq[ScNamedElement]): Seq[ScNamedElement] =
    elements.groupBy(nameOf(_, withReturnType = false)).iterator.flatMap {
      case ("_", _) => Nil
      case (_, clashed) if clashed.size > 1 => clashesOf2(clashed)
      case _ => Nil
    }.toSeq

  private def clashesOf2(elements: Seq[ScNamedElement]): Iterator[ScNamedElement] =
    elements.head match {
      case fun: ScFunction if fun.hasParameters && !fun.getParent.is[ScBlockExpr]  =>
        val (withDifferentReturnTypes, withSameReturnTypes) = elements
          .map(_.asInstanceOf[ScFunction])
          .groupBy(fun => erasedReturnType(fun, fun.getContext.is[ScRefinement]))
          .values
          .partition(_.size == 1)

        clashesOf3(withDifferentReturnTypes.flatten.to(ArraySeq)) ++ withSameReturnTypes.iterator.flatten
      case _ =>
        elements.iterator
    }

  private def clashesOf3(elements: Seq[ScFunction]): Iterator[ScFunction] = {
    // At this point all elements have the same erasure parameter types but different erasure return types
    // But they can still clash if they have equivalent parameter types, so we have to check them against each other
    val result = Set.newBuilder[ScFunction]
    for {
      (a, ai) <- elements.iterator.zipWithIndex
      b <- elements.iterator.drop(ai + 1)
      if a.parametersTypes.zip(b.parametersTypes).forall { case (a, b) => a equiv b }
    } result ++= Seq(a, b)

    result.result().iterator
  }

  private def nameOf(element: ScNamedElement, withReturnType: Boolean = true): String = element match {
    case f: ScFunction if !f.getParent.is[ScBlockExpr] =>
      ScalaNamesUtil.clean(f.name) + signatureOf(f, withReturnType)
    case _ =>
      ScalaNamesUtil.clean(element.name)
  }

  private def signatureOf(f: ScFunction, withReturnType: Boolean): String = {
    if (f.parameters.isEmpty)
      ""
    else {
      val isInStructuralType = f.getContext.is[ScRefinement]
      val params = f.paramClauses.clauses.map(format(_, eraseParamType = !isInStructuralType)).mkString
      val returnType =
        if (withReturnType && !isInStructuralType) erasedReturnType(f, isInStructuralType)
        else ""
      params + returnType
    }
  }

  private def erasedReturnType(f: ScFunction, isInStructuralType: Boolean): String = {
    if (!isInStructuralType) erased(f.returnType.getOrAny.removeAliasDefinitions()).canonicalText
    else ""
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

  private def format(clause: ScParameterClause, eraseParamType: Boolean) = {
    val parts = clause.parameters.map { p =>
      val `=>` = if (p.isCallByNameParameter) " => " else ""
      val `*` = if (p.isRepeatedParameter) "*" else ""

      val paramType = p.`type`().getOrAny.removeAliasDefinitions()
      val erasedType =
        if (eraseParamType) erased(paramType)
        else paramType

      `=>` + erasedType.canonicalText + `*`
    }
    parts.mkString("(", ", ", ")")
  }
}