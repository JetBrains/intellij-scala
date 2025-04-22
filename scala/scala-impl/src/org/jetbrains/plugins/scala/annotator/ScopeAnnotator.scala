package org.jetbrains.plugins.scala.annotator

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScopeAnnotator.Definitions
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
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.psi.types.api.{JavaArrayType, ParameterizedType, StdTypes, TypeParameterType, arrayType}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{AliasType, Context, ScLiteralType, ScParameterizedType, ScType}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.collection.immutable.ArraySeq
import scala.collection.mutable.ArrayBuffer

trait ScopeAnnotator extends ElementAnnotator[ScalaPsiElement] {

  override def annotate(element: ScalaPsiElement, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    if (typeAware) {
      annotateScope(element)
    }
  }

  //Do not process class parameters, template body and early definitions separately
  //process them in a single pass for the whole template definition
  protected def shouldAnnotate(element: PsiElement): Boolean = ScalaPsiUtil.isScope(element) && (element match {
    case _: ScTemplateBody => false
    case _: ScEarlyDefinitions => false
    case (_: ScParameters) & Parent(_: ScPrimaryConstructor) => false
    case _ => true
  }) || element.is[ScTemplateDefinition]

  def annotateScope(element: PsiElement)
                   (implicit holder: ScalaAnnotationHolder): Unit = {
    if (!shouldAnnotate(element))
      return

    def checkScope(elements: Iterable[PsiElement]): Unit = {
      val definitions = definitionsIn(elements)
      val clashesSet = clashesOf(definitions)
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

  protected final def clashesOf(definitions: Definitions): Set[ScNamedElement] = {
    val clashes =
      clashesOf(definitions.functions) ++
        clashesOf(definitions.parameterless) ++
        clashesOf(definitions.types) ++
        clashesOf(definitions.fieldLike)

    clashes.toSet
  }

  private def definitionsIn(elements: Iterable[PsiElement]): Definitions = {
    var definitions = Definitions.empty

    elements.foreach { element =>
      val classParams: List[ScClassParameter] = element match {
        case clazz: ScConstructorOwner => clazz.parameters.toList
        case _ => Nil
      }
      definitions = definitions.withClassParams(classParams)

      val children = element match {
        case en: ScEnum               => en.members ++ en.cases //TODO: remove this line after SCL-21270 is fixed
        case td: ScTemplateDefinition => td.members
        case _                        => element.children
      }
      children.foreach {
        //stop processing when found another scope
        _.depthFirst(!ScalaPsiUtil.isScope(_)).foreach { e =>
          definitions += e
        }
      }
    }

    definitions
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
      case ("_", _)                         => Nil
      case (_, clashed) if clashed.size > 1 => clashesOf2(clashed)
      case _                                => Nil
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
      element = a
      b <- elements.iterator.drop(ai + 1)
      if a
        .parametersTypes(withExtension = true)
        .zip(b.parametersTypes(withExtension = true))
        .forall { case (a, b) => a.equiv(b)(Context(element)) }
    } result ++= Seq(a, b)

    result.result().iterator
  }

  protected def elementName(element: ScNamedElement): String =
    ScalaNamesUtil.clean(element.name)

  private def nameOf(
    element: ScNamedElement,
    forPresentableText: Boolean,
    withReturnType: Boolean = true
  ): String = element match {
    case f: ScFunction if !f.getParent.is[ScBlockExpr] =>
      elementName(f) + signatureOf(f, withReturnType, forPresentableText = forPresentableText)
    case _ =>
      elementName(element)
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
      val returnType = f.returnType.getOrAny.removeAliasDefinitions()(Context.Empty)
      erased(returnType, forPresentableText).canonicalText
    }
    else ""
  }

  private def erased(t: ScType, forPresentableText: Boolean): ScType = {
    val stdTypes = StdTypes.instance(t.projectContext)

    t.updateRecursively {
      case t @ AliasType(ta: ScTypeAliasDeclaration, _, _, _) if ta.hasModifierProperty("opaque") => t match {
        case ParameterizedType(d, args) => ScParameterizedType(d, args.map(erased(_, forPresentableText)))
        case t => t
      }
      //during erasure literal types collapse into widened types
      case lit: ScLiteralType => lit.wideType
      case ScProjectionType(_, element: Typeable) => element.`type`().map {
        case literalType: ScLiteralType => literalType.widen
        case other => other
      }.getOrAny

      // array types are not erased
      case arrayType(inner) =>
        JavaArrayType(erased(inner, forPresentableText))
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
    val paramTypeExpanded = paramType.removeAliasDefinitions()(Context.Empty)
    val erasedType =
      if (eraseParamType) erased(paramTypeExpanded, forPresentableText)
      else paramTypeExpanded

    `=>` + erasedType.canonicalText + `*`
  }
}

object ScopeAnnotator extends ScopeAnnotator {
  final case class Definitions(types: List[ScNamedElement],
                               functions: List[ScFunction],
                               parameterless: List[ScNamedElement],
                               fieldLike: List[ScNamedElement]) {
    def withClassParams(classParams: List[ScClassParameter]): Definitions = {
      val (paramPrivateThis, paramNotPrivateOther) = classParams.partition(_.isPrivateThis)
      this.copy(
        parameterless = paramNotPrivateOther ::: parameterless,
        fieldLike = paramPrivateThis ::: fieldLike
      )
    }

    def +(element: PsiElement): Definitions = add(element)

    def add(element: PsiElement): Definitions = element match {
      case e: ScObject =>
        copy(
          parameterless = e :: parameterless,
          fieldLike = e :: fieldLike,
        )
      case e: ScFunction  =>
        if (e.typeParameters.isEmpty) { //generic functions are not supported
          copy(
            functions = e :: functions,
            parameterless = if (e.parameters.isEmpty || e.getContext.is[ScBlockExpr]) {
              e :: parameterless
            } else parameterless,
          )
        } else this
      case e: ScTypedDefinition =>
        copy(
          fieldLike = e :: fieldLike,
          parameterless = if (generatesFunction(e)) {
            e :: parameterless
          } else parameterless,
        )
      case e: ScTypeAlias => copy(types = e :: types)
      case e: ScTypeParam => copy(types = e :: types)
      case e: ScClass  =>
        val isCaseClassWithoutCompanion = e.isCase && e.baseCompanion.isEmpty
        copy(
          types = e :: types,
          //add a synthetic companion
          parameterless = if (isCaseClassWithoutCompanion) e :: parameterless else parameterless,
          fieldLike = if (isCaseClassWithoutCompanion) e :: fieldLike else fieldLike,
        )
      case e: ScTypeDefinition => copy(types = e :: types)
      case _ => this
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
  }

  object Definitions {
    def empty: Definitions = Definitions(Nil, Nil, Nil, Nil)
  }
}
