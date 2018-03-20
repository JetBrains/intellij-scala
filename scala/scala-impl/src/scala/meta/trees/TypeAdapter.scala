package scala.meta.trees

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.PsiMethodExt
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScTypeAliasDeclaration}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScInfixTypeElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameterType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.{api => p, types => ptype}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType

import scala.collection.immutable.Seq
import scala.language.postfixOps
import scala.meta.collections._
import scala.meta.internal.{semantic => h}
import scala.meta.trees.error._
import scala.{meta => m, Seq => _}

trait TypeAdapter {
  self: TreeConverter =>

  private val typeCache = TwoWayCache[ptype.ScType, m.Type]()
  private val typeElementCache = TwoWayCache[ScTypeElement, m.Type]()
  private val psiElementTypeChache = TwoWayCache[PsiElement, m.Type]()

  def toType(tp: ScTypeElement): m.Type = {
    ProgressManager.checkCanceled()
    typeElementCache.getOrElseUpdate(tp, {
      tp match {
        case t: ScSimpleTypeElement if dumbMode =>
          t.reference match {
            case Some(ref) =>
              ref.qualifier.map(qual=>m.Type.Select(getTypeQualifier(qual.asInstanceOf[ScReferenceElement]), toTypeName(ref)))
                .getOrElse(toTypeName(ref))
            case None => m.Type.Name(t.getText)
          }
        case t: ScSimpleTypeElement =>
          val s = ScSubstitutor(ScSubstitutor.cache)
          toType(s.subst(t.calcType))
//        case t: ScReferenceElement if dumbMode =>
//          t.qualifier.map(qual=>m.Type.Select(getTypeQualifier(qual.asInstanceOf[ScReferenceElement]), toTypeName(t)))
//            .getOrElse(toTypeName(t))
        case t: ScFunctionalTypeElement =>
          toType(t.paramTypeElement) match {
            case m.Type.Tuple(elements) => m.Type.Function(elements, toType(t.returnTypeElement.get))
            case param => m.Type.Function(Seq(param), toType(t.returnTypeElement.get))
          }
        case t: ScParameterizedTypeElement =>
          m.Type.Apply(toType(t.typeElement), t.typeArgList.typeArgs.toStream.map(toType))
        case t: ScInfixTypeElementImpl =>
          m.Type.ApplyInfix(toType(t.left), m.Type.Name(t.operation.refName), toType(t.rightOption.get))
        case t: ScTupleTypeElement =>
          m.Type.Tuple(Seq(t.components.map(toType): _*))
        case t: ScWildcardTypeElement =>
          m.Type.Placeholder(typeBounds(t))
        case t: ScCompoundTypeElement =>
          t.components
            .dropRight(1)
            .foldLeft(toType(t.components.last))((mtp, stp) => m.Type.With(toType(stp), mtp))
        case t: ScParenthesisedTypeElement =>
          t.innerElement match {
            case Some(t: ScInfixTypeElement) => m.Type.ApplyInfix(toType(t.left), toTypeName(t.operation), toType(t.rightOption.get))
            case _ => unreachable
          }
        case t: ScTypeVariableTypeElement => die("i cannot into type variables")
        case t: ScExistentialTypeElement =>
          val clauses = Seq(t.clause.declarations map {
            case tp: ScTypeAliasDeclaration => toTypeDecl(tp)
            case other => other ?!
          }: _*)
          val quantified = toType(t.quantified)
          m.Type.Existential(quantified, clauses)
        case other: ScTypeElement if dumbMode =>
          m.Type.Name(other.getText)
        case other: ScTypeElement =>
          LOG.warn(s"Using slow type conversion of type element ${other.getClass}: ${other.getText}")
          toType(other.`type`())
        case other => other ?!
      }
    })
  }

  private def getTypeQualifier(ref: ScReferenceElement): m.Term.Ref = {
    ref.qualifier match {
      case Some(r: ScReferenceElement) => m.Term.Select(getTypeQualifier(r), toTermName(ref))
      case None => toTermName(ref)
      case _ => unreachable
    }
  }

  def toType(tr: TypeResult): m.Type = {
    import org.jetbrains.plugins.scala.lang.psi.types.result._
    tr match {
      case Right(res) => toType(res)
      case Failure(cause) => throw new ScalaMetaTypeResultFailure(cause)
    }
  }

  def toType(elem: PsiElement): m.Type = {
    ProgressManager.checkCanceled()
    psiElementTypeChache.getOrElseUpdate(elem, {
      elem match {
        case t: typedef.ScTemplateDefinition if dumbMode =>
          m.Type.Name(t.name)
        case t: typedef.ScTemplateDefinition =>
          val s = ScSubstitutor(ScSubstitutor.cache)
          toType(s.subst(t.`type`().get)) // FIXME: what about typing context?
        case t: ScPackaging =>
          m.Type.Singleton(toTermName(t.reference.get))//.setTypechecked
        case t: ScConstructor => ???
//          m.Type.Method(toParams(Seq(t.arguments:_*)), toType(t.newTemplate.get.getType(TypingContext.empty))).setTypechecked
        case t: ScPrimaryConstructor => ???
//          m.Type.Method(Seq(t.parameterList.clauses.map(convertParamClause):_*), toType(t.containingClass)).setTypechecked
        case t: ScFunctionDefinition => ???
//          m.Type.Method(Seq(t.parameterList.clauses.map(convertParamClause):_*), toType(t.getTypeWithCachedSubst)).setTypechecked
        case t: ScFunction =>
          m.Type.Function(Seq(t.parametersTypes.map(toType(_, t).asInstanceOf[m.Type.Arg]): _*), toType(t.returnType)) //.setTypechecked
        case t: ScParameter if dumbMode =>
          m.Type.Name(t.getText)
        case t: ScParameter =>
          val s = ScSubstitutor(ScSubstitutor.cache)
          toType(s.subst(t.typeElement.get.`type`().get))
        case t: ScTypedDefinition if dumbMode =>
          m.Type.Name(t.name)
        case t: ScTypedDefinition =>
          t.getTypeWithCachedSubst match {
            case Right(res) => toType(res)
            case Failure(cause) => unresolved(cause)
          }
        case t: ScReferenceElement if dumbMode =>
          m.Type.Name(t.refName)
        case t: ScReferenceElement =>
          t.bind() match {
            case Some(result) => toType(result.element)
            case None => m.Type.Placeholder(m.Type.Bounds(None, None))
          }
        case t: PsiPackage if t.getName == null =>
          m.Type.Singleton(std.rootPackageName)//.setTypechecked
        case t: PsiPackage =>
          m.Type.Singleton(toTermName(t))//.setTypechecked
        case t: PsiClass =>
          m.Type.Name(t.getName)//.withAttrsFor(t)
        case t: PsiMethod => t ???
//          m.Type.Method(Seq(t.getParameterList.getParameters
//            .map(Compatibility.toParameter)
//            .map(i=> convertParam(i.paramInCode.get))
//            .toStream),
//            toType(ScTypePsiTypeBridge.toScType(t.getReturnType, t.getProject))).setTypechecked
        case other => other ?!
      }
    })
  }

  def toType(tp: ptype.ScType, pivot: PsiElement = null): m.Type = {
    ProgressManager.checkCanceled()
    typeCache.getOrElseUpdate(tp, {
      tp.isAliasType match {
        case Some(AliasType(ta, lower, upper)) => return toTypeName(ta)
        case _ =>
      }
      tp match {
        case t: ptype.ScParameterizedType =>
          m.Type.Apply(toType(t.designator), Seq(t.typeArguments.map(toType(_)): _*))//.setTypechecked
        case t: ptype.api.designator.ScThisType =>
          toTypeName(t.element)//.setTypechecked
        case t: ptype.api.designator.ScProjectionType =>
          t.projected match {
            case tt: ptype.api.designator.ScThisType =>
              m.Type.Select(toTermName(tt.element), toTypeName(t.actualElement))//.setTypechecked
            case _ =>
              m.Type.Project(toType(t.projected), toTypeName(t.actualElement))//.setTypechecked
          }
        case t: ptype.api.designator.ScDesignatorType =>
          if (t.element.isSingletonType)
            toSingletonType(t.element)
          else
            toTypeName(t.element)
        case t: ptype.ScCompoundType if t.components.size < 2 =>
          unreachable("Number of parts in compound type must be >= 2")
        case t: ptype.ScCompoundType =>
          t.components
            .dropRight(1)
            .foldLeft(toType(t.components.last))((mtp, stp) => m.Type.With(toType(stp), mtp))
        case t: ptype.ScExistentialType =>
          val wcards = t.wildcards.map {wc =>
//            val (name, args, lower, upper) = wc
            val ubound = if (wc.upper.isAny)      None else Some(toType(wc.upper))
            val lbound = if (wc.lower.isNothing)  None else Some(toType(wc.lower))
            m.Decl.Type(Nil, m.Type.Name(wc.name),
                //FIXME: pass actual prefix, when solution for recursive prefix computation is ready
               // .withAttrs(h.Denotation.Single(h.Prefix.None, toSymbolWtihParent(wc.name, pivot, h.ScalaSig.Type(wc.name))))
               // .setTypechecked,
              Nil, m.Type.Bounds(lbound, ubound))//.setTypechecked
          }
          m.Type.Existential(toType(t.quantified), wcards)//.setTypechecked
        case t: ptype.api.StdType =>
          toStdTypeName(t)
        case t: TypeParameterType =>
          m.Type.Name(t.name)//.withAttrsFor(t.nameAndId._2)
        case t: ptype.ScType =>
          LOG.warn(s"Unknown type: ${t.getClass} - ${t.canonicalText}")
          m.Type.Name(t.canonicalText)//.withAttrs(h.Denotation.None)
      }
    })
  }

  def toSingletonType(elem: PsiElement): m.Type.Singleton = {
    m.Type.Singleton(toTermName(elem))//.setTypechecked
  }

  def toTypeParams(tp: TypeParameterType): m.Type.Param = {
    val ubound = if (tp.upperType.isAny)      None else Some(toType(tp.upperType))
    val lbound = if (tp.lowerType.isNothing)  None else Some(toType(tp.lowerType))
    m.Type.Param(
      if(tp.isCovariant) m.Mod.Covariant() :: Nil else if(tp.isContravariant) m.Mod.Contravariant() :: Nil else Nil,
      if (tp.name != "_") m.Type.Name(tp.name) else m.Name.Anonymous(),//.withAttrs(h.Denotation.None).setTypechecked,
      Seq(tp.arguments.map(toTypeParams):_*),
      m.Type.Bounds(lbound, ubound), Nil, Nil
    )
  }

  def toTypeParams(tp: p.statements.params.ScTypeParam): m.Type.Param = {
    m.Type.Param(
      if(tp.isCovariant) m.Mod.Covariant() :: Nil else if(tp.isContravariant) m.Mod.Contravariant() :: Nil else Nil,
      if (tp.name != "_") toTypeName(tp) else m.Name.Anonymous(),//.withAttrsFor(tp),
      Seq(tp.typeParameters.map(toTypeParams):_*),
      typeBounds(tp),
      viewBounds(tp),
      contextBounds(tp)
    )//.setTypechecked
  }

  def toTypeParams(tp: PsiTypeParameter): m.Type.Param = {
    m.Type.Param(
      m.Mod.Covariant() :: Nil,
      toTypeName(tp),
      Seq(tp.getTypeParameters.map(toTypeParams):_*),
      m.Type.Bounds(None, None),
      Seq.empty, Seq.empty
    )//.setTypechecked
  }

  def viewBounds(tp: ScTypeBoundsOwner): Seq[m.Type] = {
    Seq(tp.viewTypeElement.map(toType):_*)
  }

  def contextBounds(tp: ScTypeBoundsOwner): Seq[m.Type] = {
    Seq(tp.contextBoundTypeElement.map(toType):_*)
  }

  def typeBounds(tp: ScTypeBoundsOwner): m.Type.Bounds = {
    m.Type.Bounds(tp.lowerTypeElement.map(toType), tp.upperTypeElement.map(toType))//.setTypechecked
  }

  def returnType(tr: ptype.result.TypeResult): m.Type = {
    import ptype.result._
    tr match {
      case Right(t) => toType(t)
      case Failure(cause) =>
        LOG.warn(s"Failed to infer return type($cause)")
        m.Type.Name("Unit")//.setTypechecked
    }
  }


  def fromType(tpe: m.Type): ptype.ScType = ???
}
