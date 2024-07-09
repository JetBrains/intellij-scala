package org.jetbrains.plugins.scala.annotator

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.element.ElementAnnotator
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScRefinement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScFor, ScGenerator}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScParameterClause, ScParameters, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScConstructorOwner, ScEnum, ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{JavaArrayType, ParameterizedType, StdTypes, TypeParameterType, arrayType}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScParameterizedType, ScType}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.collection.immutable.ArraySeq
import scala.collection.mutable.ArrayBuffer

object ScopeAnnotator extends ElementAnnotator[ScalaPsiElement] {

  private case class Definitions(types: List[ScNamedElement],
                                 functions: List[ScFunction],
                                 parameterless: List[ScNamedElement],
                                 fieldLike: List[ScNamedElement])

  override def annotate(element: ScalaPsiElement, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit =
    annotateScope(element)

  def annotateScope(element: PsiElement)
                   (implicit holder: ScalaAnnotationHolder): Unit = {
    //Do not process class parameters, template body and early definitions separately
    //process them in a single pass for the whole template definition
    val shouldAnnotate = ScalaPsiUtil.isScope(element) && (element match {
      case _: ScTemplateBody => false
      case _: ScEarlyDefinitions => false
      case (_: ScParameters) & Parent(_: ScPrimaryConstructor) => false
      case _ => true
    }) || element.is[ScTemplateDefinition]

    if (!shouldAnnotate)
      return

    def checkScope(elements: Iterable[PsiElement]): Unit = {
      val Definitions(types, functions, parameterless, fieldLikes) = definitionsIn(elements)

      val clashes =
        clashesOf(functions) ++
        clashesOf(parameterless) ++
        clashesOf(types) ++
        clashesOf(fieldLikes)

      val clashesSet = clashes.toSet
      clashesSet.foreach { e =>
        val element = Option(e.getNameIdentifier).getOrElse(e)
        val innerText = nameOf(e, forPresentableText = true)
        holder.createErrorAnnotation(element, ScalaBundle.message("id.is.already.defined", innerText))
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

  private def definitionsIn(elements: Iterable[PsiElement]): Definitions = {
    var types: List[ScNamedElement] = Nil
    var functions: List[ScFunction] = Nil
    var parameterless: List[ScNamedElement] = Nil
    var fieldLike: List[ScNamedElement] = Nil
    var classParameters: List[ScClassParameter] = Nil

    elements.foreach { element =>
      val classParams: List[ScClassParameter] = element match {
        case clazz: ScConstructorOwner => clazz.parameters.toList
        case _ => Nil
      }
      val (paramPrivateThis, paramNotPrivateOther) = classParams.partition(_.isPrivateThis)
      classParameters :::= classParams
      fieldLike :::= paramPrivateThis
      parameterless :::= paramNotPrivateOther

      val children = element match {
        case en: ScEnum               => en.members ++ en.cases //TODO: remove this line after SCL-21270 is fixed
        case td: ScTemplateDefinition => td.members
        case _                        => element.children
      }
      children.foreach {
        //stop processing when found another scope
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
            if (e.isCase && e. baseCompanion.isEmpty) { //add a synthetic companion
              parameterless ::= e
              fieldLike ::= e
            }
            types ::= e
          case e: ScTypeDefinition => types ::= e
          case _ =>
        }
      }
    }

    Definitions(types, functions, parameterless, fieldLike)
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
  //  1. We group together all elements that have the same name and the same erasure parameter types.
  //  2. All elements that end up in the same group and are not functions with parameters are already clashes.
  //     The functions are now grouped by their erasure return type.
  //     Functions in the same group have the exact same erasure signature and must therefore be clashes.
  //  3. The remaining functions will not clash if they do not have equivalent parameter types.
  private def clashesOf(elements: Seq[ScNamedElement]): Seq[ScNamedElement] = {
    val nameToClashes = elements.groupBy(nameOf(_, withReturnType = false, forPresentableText = false))
    nameToClashes.iterator.flatMap {
      case ("_", _) => Nil
      case (_, clashed) if clashed.size > 1 => clashesOf2(clashed)
      case _ => Nil
    }.toSeq
  }

  private def clashesOf2(elements: Seq[ScNamedElement]): Iterator[ScNamedElement] =
    elements.head match {
      case fun: ScFunction if fun.hasParameters && !fun.getParent.is[ScBlockExpr]  =>
        val (withDifferentReturnTypes, withSameReturnTypes) = elements
          .map(_.asInstanceOf[ScFunction])
          .groupBy(fun => erasedReturnType(fun, fun.getContext.is[ScRefinement], forPresentableText = false))
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

  private def nameOf(
    element: ScNamedElement,
    forPresentableText: Boolean,
    withReturnType: Boolean = true
  ): String = element match {
    case f: ScFunction if !f.getParent.is[ScBlockExpr] =>
      ScalaNamesUtil.clean(f.name) + signatureOf(f, withReturnType, forPresentableText = forPresentableText)
    case _ =>
      ScalaNamesUtil.clean(element.name)
  }

  private def signatureOf(
    f: ScFunction,
    withReturnType: Boolean,
    forPresentableText: Boolean
  ): String = {
    if (!f.isExtensionMethod && f.parameters.isEmpty)
      ""
    else {
      val isInStructuralType = f.getContext.is[ScRefinement]
      val params = if (f.isExtensionMethod) f.parameterClausesWithExtension() else f.paramClauses.clauses
      val formattedParams = params.map(format(_, eraseParamType = !isInStructuralType, forPresentableText)).mkString
      val returnType =
        if (withReturnType && !isInStructuralType) erasedReturnType(f, isInStructuralType, forPresentableText)
        else ""
      formattedParams + returnType
    }
  }

  private def erasedReturnType(f: ScFunction, isInStructuralType: Boolean, forPresentableText: Boolean): String = {
    if (!isInStructuralType) {
      val returnType = f.returnType.getOrAny.removeAliasDefinitions()
      erased(returnType, forPresentableText).canonicalText
    }
    else ""
  }

  private def erased(t: ScType, forPresentableText: Boolean): ScType = {
    val stdTypes = StdTypes.instance(t.projectContext)

    t.updateRecursively {
      //during erasure literal types collapse into widened types
      case lit: ScLiteralType => lit.wideType
      case ScProjectionType(_, element: Typeable) => element.`type`().map {
        case literalType: ScLiteralType => literalType.widen
        case other => other
      }.getOrAny

      // array types are not erased
      case arrayType(inner) =>
        JavaArrayType(erased(inner, forPresentableText))
      case pt@ParameterizedType(ScDesignatorType(ta: ScTypeAlias), Seq(arg)) if ta.qualifiedNameOpt.contains("scala.IArray") =>
        if (forPresentableText) //use IArray instead of Array when presenting text
          ScParameterizedType(pt.designator, pt.typeArguments.map(erased(_, forPresentableText)))
        else
          JavaArrayType(erased(arg, forPresentableText))
      case pt: ParameterizedType => pt.designator
      case tpt: TypeParameterType => tpt.upperType
      case stdTypes.Any | stdTypes.AnyVal => stdTypes.AnyRef
    }
  }

  private def format(
    clause: ScParameterClause,
    eraseParamType: Boolean,
    forPresentableText: Boolean
  ): String = {
    val parts = clause.parameters.map(formatParameter(_, eraseParamType, forPresentableText))
    parts.mkString("(", ", ", ")")
  }

  private def formatParameter(
    p: ScParameter,
    eraseParamType: Boolean,
    forPresentableText: Boolean
  ): String = {
    val `=>` = if (p.isCallByNameParameter) " => " else ""
    val `*` = if (p.isRepeatedParameter) "*" else ""

    val paramType = p.`type`().getOrAny
    // We need opaque types RHS to distinguish array types (e.g. IArray, see SCL-22062).
    // However, when we show the type in the error tooltip,
    // we don't expand opaque types because for user RHS is an implementation detail.
    val paramTypeExpanded = paramType.removeAliasDefinitions()
    val erasedType =
      if (eraseParamType) erased(paramTypeExpanded, forPresentableText)
      else paramTypeExpanded

    `=>` + erasedType.canonicalText + `*`
  }
}