package org.jetbrains.plugins.scala
package lang
package psi
package types

import decompiler.DecompilerUtil
import impl.ScalaPsiManager
import impl.toplevel.synthetic.{SyntheticClasses, ScSyntheticClass}
import nonvalue.NonValueType
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import result.{Failure, Success, TypingContext}
import com.intellij.openapi.project.{DumbServiceImpl, Project}
import api.base.patterns.ScBindingPattern
import org.apache.commons.lang.StringEscapeUtils
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import refactoring.util.ScTypeUtil
import api.toplevel.typedef.{ScTypeDefinition, ScClass, ScObject}
import api.statements._
import api.toplevel.ScTypedDefinition

trait ScType {
  final def equiv(t: ScType): Boolean = Equivalence.equiv(this, t)

  /**
   * Checks, whether the following assignment is correct:
   * val x: t = (y: this)
   */
  final def conforms(t: ScType): Boolean = Conformance.conforms(t, this)

  final def weakConforms(t: ScType): Boolean = Conformance.conforms(t, this, true)

  final def presentableText = ScType.presentableText(this)
  
  final def canonicalText = ScType.canonicalText(this)
  
  override def toString = presentableText

  def isValue: Boolean

  final def isStable: Boolean = ScType.isStable(this)

  def inferValueType: ValueType

  /**
   * This method is important for parameters expected type.
   * There shouldn't be any abstract type in this expected type.
   */
  def removeAbstracts = this

  def updateThisType(place: PsiElement): ScType = this

  def updateThisType(tp: ScType): ScType = this
}

trait ValueType extends ScType{
  def isValue = true

  def inferValueType: ValueType = this
}

abstract case class StdType(val name : String, val tSuper: Option[StdType]) extends ValueType {
  /**
   * Return wrapped to option appropriate synthetic class.
   * In dumb mode returns None (or before it ends to register classes).
   * @param project in which project to find this class
   * @return If possible class to represent this type.
   */
  def asClass(project : Project): Option[ScSyntheticClass] = {
    if (SyntheticClasses.get(project).isClassesRegistered)
      Some(SyntheticClasses.get(project).byName(name).get)
    else None
  }
}

case object Any extends StdType("Any", None)

case object Null extends StdType("Null", Some(AnyRef))

case object AnyRef extends StdType("AnyRef", Some(Any))

case object Nothing extends StdType("Nothing", None)

case object Singleton extends StdType("Singleton", Some(AnyRef))

case object AnyVal extends StdType("AnyVal", Some(Any))

abstract case class ValType(override val name: String) extends StdType(name, Some(AnyVal))

object Unit extends ValType("Unit")
object Boolean extends ValType("Boolean")
object Char extends ValType("Char")
object Int extends ValType("Int")
object Long extends ValType("Long")
object Float extends ValType("Float")
object Double extends ValType("Double")
object Byte extends ValType("Byte")
object Short extends ValType("Short")

object ScType {
  def create(psiType: PsiType, project: Project, scope: GlobalSearchScope = null, deep: Int = 0): ScType = {if (deep > 2) return Any; psiType match {
    case classType: PsiClassType => {
      val result = classType.resolveGenerics
      result.getElement match {
        case tp: PsiTypeParameter => ScalaPsiManager.typeVariable(tp)
        case clazz if clazz != null && clazz.getQualifiedName == "java.lang.Object" => AnyRef
        case clazz if clazz != null => {
          val tps = clazz.getTypeParameters
          val des = new ScDesignatorType(clazz)
          val substitutor = result.getSubstitutor
          tps match {
            case Array() => des
            case _ if classType.isRaw => {
              new ScParameterizedType(des, collection.immutable.Seq(tps.map({tp => {
                new ScExistentialArgument("_", Nil, Nothing,
                  tp.getSuperTypes.length match {
                    case 0 => Any
                    case 1 => create(tp.getSuperTypes.apply(0), project, scope, deep + 1)
                    case _ => ScCompoundType(tp.getSuperTypes.map(create(_, project, scope, deep + 1)), Seq.empty, Seq.empty, ScSubstitutor.empty)
                  })
              }}): _*))
            }
            case _ => new ScParameterizedType(des, collection.immutable.Seq(tps.map
                      (tp => {
              val psiType = substitutor.substitute(tp)
              if (psiType != null) ScType.create(psiType, project, scope, deep + 1)
              else ScalaPsiManager.typeVariable(tp)
            }).toSeq : _*))
          }
        }
        case _ => Nothing
      }
    }
    case arrayType: PsiArrayType => {
      JavaArrayType(create(arrayType.getComponentType, project, scope))
    }

    case PsiType.VOID => Unit
    case PsiType.BOOLEAN => Boolean
    case PsiType.CHAR => Char
    case PsiType.INT => Int
    case PsiType.LONG => Long
    case PsiType.FLOAT => Float
    case PsiType.DOUBLE => Double
    case PsiType.BYTE => Byte
    case PsiType.SHORT => Short
    case PsiType.NULL => Null
    case wild : PsiWildcardType => new ScExistentialArgument("_", Nil,
      if(wild.isSuper) create(wild.getSuperBound, project, scope, deep + 1) else Nothing,
      if(wild.isExtends) create(wild.getExtendsBound, project, scope, deep + 1) else Any)
    case capture : PsiCapturedWildcardType =>
      val wild = capture.getWildcard
      new ScSkolemizedType("_", Nil,
        if(wild.isSuper) create(capture.getLowerBound, project, scope) else Nothing,
        if(wild.isExtends) create(capture.getUpperBound, project, scope) else Any)
    case null => Any//new ScExistentialArgument("_", Nil, Nothing, Any) // raw type argument from java
    case _ => throw new IllegalArgumentException("psi type " + psiType + " should not be converted to scala type")
  }}

  def toPsi(t: ScType, project: Project, scope: GlobalSearchScope): PsiType = {
    if (t.isInstanceOf[NonValueType]) return toPsi(t.inferValueType, project, scope)
    def javaObj = JavaPsiFacade.getInstance(project).getElementFactory.createTypeByFQClassName("java.lang.Object", scope)
    t match {
      case Unit => PsiType.VOID
      case Boolean => PsiType.BOOLEAN
      case Char => PsiType.CHAR
      case Int => PsiType.INT
      case Long => PsiType.LONG
      case Float => PsiType.FLOAT
      case Double => PsiType.DOUBLE
      case Byte => PsiType.BYTE
      case Short => PsiType.SHORT
      case Null => PsiType.NULL
      case fun: ScFunctionType => fun.resolveFunctionTrait(project) match {
        case Some(tp) => toPsi(tp, project, scope) case _ => javaObj
      }
      case tuple: ScTupleType => tuple.resolveTupleTrait(project) match {
        case Some(tp) => toPsi(tp, project, scope) case _ => javaObj
      }
      case ScCompoundType(Seq(t, _*), _, _, _) => toPsi(t, project, scope)
      case ScDesignatorType(c: PsiClass) => JavaPsiFacade.getInstance(project).getElementFactory.createType(c, PsiSubstitutor.EMPTY)
      case ScParameterizedType(ScDesignatorType(c: PsiClass), args) =>
        if (c.getQualifiedName == "scala.Array" && args.length == 1)
          new PsiArrayType(toPsi(args(0), project, scope))
        else {
          val subst = args.zip(c.getTypeParameters).foldLeft(PsiSubstitutor.EMPTY)
                    {case (s, (targ, tp)) => s.put(tp, toPsi(targ, project, scope))}
          JavaPsiFacade.getInstance(project).getElementFactory.createType(c, subst)
        }
      case ScParameterizedType(ScProjectionType(pr, element, subst), args) => element match {
        case c: PsiClass => if (c.getQualifiedName == "scala.Array" && args.length == 1)
          new PsiArrayType(toPsi(args(0), project, scope))
        else {
          val subst = args.zip(c.getTypeParameters).foldLeft(PsiSubstitutor.EMPTY)
                    {case (s, (targ, tp)) => s.put(tp, toPsi(targ, project, scope))}
          JavaPsiFacade.getInstance(project).getElementFactory.createType(c, subst)
        }
        case _ => javaObj
      }
      case JavaArrayType(arg) => new PsiArrayType(toPsi(arg, project, scope))

      case ScProjectionType(pr, element, subst) => element match {
        case clazz: PsiClass => {
          clazz match {
            case syn: ScSyntheticClass => toPsi(syn.t, project, scope)
            case _ => {
              val fqn = clazz.getQualifiedName
              JavaPsiFacade.getInstance(project).getElementFactory.createTypeByFQClassName(if (fqn != null) fqn else "java.lang.Object", scope)
            }
          }
        }
        case elem: ScTypeAliasDefinition => {
          elem.aliasedType(TypingContext.empty) match {
            case Success(t, _) => toPsi(t, project, scope)
            case Failure(_, _) => javaObj
          }
        }
        case _ => javaObj
      }
      case _ => javaObj
    }
  }

  def extractClass(t: ScType): Option[PsiClass] = extractClassType(t).map(_._1)

  def extractClassType(t: ScType): Option[Pair[PsiClass, ScSubstitutor]] = t match {
    case n: NonValueType => extractClassType(n.inferValueType)
    case ScDesignatorType(clazz: PsiClass) => Some(clazz, ScSubstitutor.empty)
    case ScDesignatorType(ta: ScTypeAliasDefinition) =>
      extractClassType(ta.aliasedType(TypingContext.empty).getOrElse(return None))
    case proj@ScProjectionType(p, elem, subst) => elem match {
      case c: PsiClass => Some((c, subst))
      case t: ScTypeAliasDefinition =>
        extractClassType(subst.subst(t.aliasedType(TypingContext.empty).getOrElse(return None)))
      case _ => None
    }
    case p@ScParameterizedType(t1, _) => {
      extractClassType(t1) match {
        case Some((c, s)) => Some((c, s.followed(p.substitutor)))
        case None => None
      }
    }
    case tuple@ScTupleType(comp) => {
      tuple.resolveTupleTrait match {
        case Some(clazz) => extractClassType(clazz)
        case _ => None
      }
    }
    case fun: ScFunctionType => {
      fun.resolveFunctionTrait match {
        case Some(tp) => extractClassType(tp)
        case _ => None
      }
    }
    case std@StdType(_, _) => Some((std.asClass(DecompilerUtil.obtainProject).getOrElse(return None), ScSubstitutor.empty))
    case _ => None
  }

  def extractDesignated(t: ScType): Option[Pair[PsiNamedElement, ScSubstitutor]] = t match {
    case ScDesignatorType(e) => Some(e, ScSubstitutor.empty)
    case proj@ScProjectionType(p, e, s) => Some((e, s))
    case p@ScParameterizedType(t1, _) => {
      extractClassType(t1) match {
        case Some((e, s)) => Some((e, s.followed(p.substitutor)))
        case None => None
      }
    }
    case _ => None
  }

  def presentableText(t: ScType) = typeText(t, {e => e.getName}, {e: PsiNamedElement => {
    e match {
      case obj: ScObject if obj.getQualifiedName == "scala.Predef" => ""
      case pack: PsiPackage => ""
      case _ => e.getName + "."
    }
  }})

  def urlText(t: ScType) = {
    def nameFun(e: PsiNamedElement, withPoint: Boolean): String = {
      e match {
        case obj: ScObject if withPoint && obj.getQualifiedName == "scala.Predef" => ""
        case e: PsiClass => "<a href=\"psi_element://" + e.getQualifiedName + "\"><code>" +
                StringEscapeUtils.escapeHtml(e.getName) +
                "</code></a>" + (if (withPoint) "." else "")
        case pack: PsiPackage if withPoint => ""
        case _ => StringEscapeUtils.escapeHtml(e.getName) + "."
      }
    }
    typeText(t, nameFun(_, false), nameFun(_, true))
  }

  //todo: resolve cases when java type have keywords as name (type -> `type`)
  def canonicalText(t: ScType) = {
    def nameFun(e: PsiNamedElement, withPoint: Boolean): String = {
      (e match {
        case c: PsiClass => {
          val qname = c.getQualifiedName
          if (qname != null && qname != c.getName /* exlude default package*/) "_root_." + qname else c.getName
        }
        case p: PsiPackage => "_root_." + p.getQualifiedName
        case _ => e.getName
      }) + (if (withPoint) "." else "")
    }
    typeText(t, nameFun(_, false), nameFun(_, true))
  }

  private def typeText(t: ScType, nameFun: PsiNamedElement => String, nameWithPointFun: PsiNamedElement => String): String = {
    val buffer = new StringBuilder
    def appendSeq(ts: Seq[ScType], sep: String) = {
      var first = true
      for (t <- ts) {
        if (!first) buffer.append(sep)
        first = false
        inner(t)
      }
    }

    def inner(t: ScType): Unit = t match {
      case ScAbstractType(tpt, lower, upper) => buffer.append("AbstractType").append(tpt.name.capitalize)
      case StdType(name, _) => buffer.append(name)
      case ScFunctionType(ret, params) => {
        buffer.append("("); appendSeq(params, ", "); buffer.append(") => "); inner(ret)
      }
      case ScThisType(clazz: ScTypeDefinition) => buffer.append(nameWithPointFun(clazz)).append("this.type")
      case ScThisType(clazz) => buffer.append("this.type")
      case ScTupleType(comps) => buffer.append("("); appendSeq(comps, ", "); buffer.append(")")
      case ScDesignatorType(e) => buffer.append(nameFun(e))
      case ScProjectionType(p, e, s) => {  //todo:
        val refName = e.getName
        p match {
          case ScDesignatorType(pack: PsiPackage) => buffer.append(nameWithPointFun(pack)).append(refName)
          case ScDesignatorType(obj: ScObject) => buffer.append(nameWithPointFun(obj)).append(refName)
          case ScDesignatorType(v: ScBindingPattern) => buffer.append(nameWithPointFun(v)).append(refName)
          case _ => inner(p); buffer.append("#").append(refName)
        }
      }
      case p: ScParameterizedType if p.getTupleType != None => inner(p.getTupleType.get)
      case p: ScParameterizedType if p.getFunctionType != None => inner(p.getFunctionType.get)
      case ScParameterizedType(des, typeArgs) => {
        inner(des); buffer.append("["); appendSeq(typeArgs, ", "); buffer.append("]")
      }
      case j@JavaArrayType(arg) => buffer.append("Array["); inner(arg); buffer.append("]")
      case ScSkolemizedType(name, _, _, _) => buffer.append(name)
      case ScTypeParameterType(name, _, _, _, _) => buffer.append(name)
      case ScUndefinedType(tpt: ScTypeParameterType) => buffer.append("NotInfered").append(tpt.name)
      case ScExistentialArgument(name, args, lower, upper) => {
        buffer.append(name)
        if (args.length > 0) {
          buffer.append("[");
          appendSeq(args, ",")
          buffer.append("]")
        }
        lower match {
          case Nothing =>
          case _ =>
            buffer.append(" >: ")
            inner(lower)
        }
        upper match {
          case Any =>
          case _ =>
            buffer.append(" <: ")
            inner(upper)
        }
      }
      case ScCompoundType(comps, decls, typeDecls, s) => {
        buffer.append(comps.map(typeText(_, nameFun, nameWithPointFun)).mkString(" with "))
        if (decls.length + typeDecls.length > 0) {
          if (!comps.isEmpty) buffer.append(" ")
          buffer.append("{")
          buffer.append(decls.map {decl =>
            decl match {
              case fun: ScFunction => {
                val buffer = new StringBuilder("")
                buffer.append("def ").append(fun.name)
                buffer.append(fun.paramClauses.clauses.map(_.parameters.map(param =>
                  ScalaDocumentationProvider.parseParameter(param, tp => typeText(s.subst(tp), nameFun, nameWithPointFun))).
                        mkString("(", ", ", ")")).mkString(""))
                for (tp <- fun.returnType) {
                  buffer.append(": ").append(typeText(s.subst(tp), nameFun, nameWithPointFun))
                }
                buffer.toString
              }
              case v: ScValue => {
                v.declaredElements.map(td => "val " + td.name + ": " +
                        typeText(s.subst(td.getType(TypingContext.empty).getOrElse(Any)), nameFun, nameWithPointFun)).
                        mkString("; ")
              }
              case v: ScVariable => {
                v.declaredElements.map(td => "val " + td.name + ": " +
                        typeText(s.subst(td.getType(TypingContext.empty).getOrElse(Any)), nameFun, nameWithPointFun)).
                        mkString("; ")
              }
              case _ => ""
            }
          }.mkString("; "))

          if (!typeDecls.isEmpty) {
            if (!decls.isEmpty) buffer.append("; ")
            buffer.append(typeDecls.map(ta => "type " + ta.getName + (if (ta.typeParameters.length > 0) (ta.typeParameters.map {param =>
              var paramText = param.getName
              if (param.isContravariant) paramText = "-" + paramText
              else if (param.isCovariant) paramText = "+" + paramText
              param.lowerBound foreach {
                case psi.types.Nothing =>
                case tp: ScType => paramText = paramText + " >: " + typeText(s.subst(tp), nameFun, nameWithPointFun)
              }
              param.upperBound foreach {
                case psi.types.Any =>
                case tp: ScType => paramText = paramText + " <: " + typeText(s.subst(tp), nameFun, nameWithPointFun)
              }
              param.viewBound foreach {
                (tp: ScType) => paramText = paramText + " <% " + typeText(s.subst(tp), nameFun, nameWithPointFun)
              }
              param.contextBound foreach {
                (tp: ScType) => paramText = paramText + " : " +
                        typeText(ScTypeUtil.stripTypeArgs(s.subst(tp)), nameFun, nameWithPointFun)
              }
              paramText
            }).mkString("[", ", ", "]") else "")).mkString("; "))
          }
          
          buffer.append("}")
        }
      }
      case ScExistentialType(q, wilds) => {
        inner(q)
        buffer.append(" forSome{");
        appendSeq(wilds, "; ");
        buffer.append("}")
      }
      case _ => null //todo
    }
    inner(t)
    buffer.toString
  }

  // TODO: Review this against SLS 3.2.1
  def isStable(t: ScType): Boolean = {
    t match {
      case x if t.conforms(Singleton) => true // TODO: this is always false, fix conforms().
      case ScThisType(_) => true
      case ScProjectionType(projected, element: ScTypedDefinition, _) => isStable(projected) && element.isStable
      // TODO: Is this needed?
      case ExpandDesignatorToProjection(projection) => isStable(projection)
      case _ => false
    }
  }
}
