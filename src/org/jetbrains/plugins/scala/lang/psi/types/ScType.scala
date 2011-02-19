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
import org.apache.commons.lang.StringEscapeUtils
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import refactoring.util.ScTypeUtil
import api.toplevel.typedef.{ScTypeDefinition, ScClass, ScObject}
import api.statements._
import api.toplevel.ScTypedDefinition
import api.base.patterns.{ScReferencePattern, ScBindingPattern}
import params.ScTypeParam

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

  @deprecated("use ScSubstitutor.subst")
  def updateThisType(place: PsiElement): ScType = this

  @deprecated("use ScSubstitutor.subst")
  def updateThisType(tp: ScType): ScType = this

  def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    (false, uSubst)
  }
}

trait ValueType extends ScType {
  def isValue = true

  def inferValueType: ValueType = this
}

abstract case class StdType(name: String, tSuper: Option[StdType]) extends ValueType {
  /**
   * Return wrapped to option appropriate synthetic class.
   * In dumb mode returns None (or before it ends to register classes).
   * @param project in which project to find this class
   * @return If possible class to represent this type.
   */
  def asClass(project: Project): Option[ScSyntheticClass] = {
    if (SyntheticClasses.get(project).isClassesRegistered)
      Some(SyntheticClasses.get(project).byName(name).get)
    else None
  }

  override def equivInner(r: ScType, subst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    (this, r) match {
      case (l: StdType, r: StdType) => (l == r, subst)
      case (AnyRef, r) => {
        ScType.extractClass(r) match {
          case Some(clazz) if clazz.getQualifiedName == "java.lang.Object" => (true, subst)
          case _ => (false, subst)
        }
      }
      case _ => (false, subst)
    }
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
  def isSingletonType(tp: ScType): Boolean = {
    tp match {
      case _: ScThisType => true
      case ScDesignatorType(v) =>
        v match {
          case t: ScTypedDefinition => t.isStable
          case _ => false
        }
      case ScProjectionType(_, elem, _) =>
        elem match {
          case t: ScTypedDefinition => t.isStable
          case _ => false
        }
      case _ => false
    }
  }

  def create(psiType: PsiType, project: Project, scope: GlobalSearchScope = null, deep: Int = 0,
             paramTopLevel: Boolean = false): ScType = {
    if (deep > 2) return Any;
    psiType match {
      case classType: PsiClassType => {
        val result = classType.resolveGenerics
        result.getElement match {
          case tp: PsiTypeParameter => ScalaPsiManager.typeVariable(tp)
          case clazz if clazz != null && clazz.getQualifiedName == "java.lang.Object" => {
            if (!paramTopLevel) AnyRef
            else Any
          }
          case c if c != null => {
            val clazz = c match {
              case o: ScObject => ScalaPsiUtil.getCompanionModule(o).getOrElse(o)
              case _ => c
            }
            val tps = clazz.getTypeParameters
            def constructTypeForClass(clazz: PsiClass): ScType = {
              val containingClass: PsiClass = clazz.getContainingClass
              if (containingClass == null) {
                return ScDesignatorType(clazz)
              } else {
                return ScProjectionType(constructTypeForClass(containingClass), clazz, ScSubstitutor.empty)
              }
            }
            val des = constructTypeForClass(clazz)
            val substitutor = result.getSubstitutor
            tps match {
              case Array() => des
              case _ if classType.isRaw => {
                new ScParameterizedType(des, collection.immutable.Seq(tps.map({tp => {
                  val arrayOfTypes: Array[PsiClassType] = tp.getExtendsListTypes ++ tp.getImplementsListTypes
                  new ScExistentialArgument("_", Nil, Nothing,
                    arrayOfTypes.length match {
                      case 0 => Any
                      case 1 => create(arrayOfTypes.apply(0), project, scope, deep + 1)
                      case _ => ScCompoundType(arrayOfTypes.map(create(_, project, scope, deep + 1)), Seq.empty, Seq.empty, ScSubstitutor.empty)
                    })
              }}): _*))
              }
              case _ => new ScParameterizedType(des, collection.immutable.Seq(tps.map
                        (tp => {
                val psiType = substitutor.substitute(tp)
                if (psiType != null) ScType.create(psiType, project, scope, deep + 1)
                else ScalaPsiManager.typeVariable(tp)
              }).toSeq: _*))
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
      case wild: PsiWildcardType => new ScExistentialArgument("_", Nil,
        if (wild.isSuper) create(wild.getSuperBound, project, scope, deep + 1) else Nothing,
        if (wild.isExtends) create(wild.getExtendsBound, project, scope, deep + 1) else Any)
      case capture: PsiCapturedWildcardType =>
        val wild = capture.getWildcard
        new ScSkolemizedType("_", Nil,
          if (wild.isSuper) create(capture.getLowerBound, project, scope) else Nothing,
          if (wild.isExtends) create(capture.getUpperBound, project, scope) else Any)
      case null => Any //new ScExistentialArgument("_", Nil, Nothing, Any) // raw type argument from java
      case _ => throw new IllegalArgumentException("psi type " + psiType + " should not be converted to scala type")
    }
  }

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
      case ScParameterizedType(proj@ScProjectionType(pr, element, subst), args) => proj.actualElement match {
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

      case proj@ScProjectionType(pr, element, subst) => proj.actualElement match {
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

  def extractClass(t: ScType, project: Option[Project] = None): Option[PsiClass] = extractClassType(t, project).map(_._1)

  def extractClassType(t: ScType, project: Option[Project] = None): Option[Pair[PsiClass, ScSubstitutor]] = t match {
    case n: NonValueType => extractClassType(n.inferValueType)
    case ScDesignatorType(clazz: PsiClass) => Some(clazz, ScSubstitutor.empty)
    case ScDesignatorType(ta: ScTypeAliasDefinition) =>
      extractClassType(ta.aliasedType(TypingContext.empty).getOrElse(return None))
    case proj@ScProjectionType(p, elem, subst) => proj.actualElement match {
      case c: PsiClass => Some((c, proj.actualSubst))
      case t: ScTypeAliasDefinition =>
        extractClassType(proj.actualSubst.subst(t.aliasedType(TypingContext.empty).getOrElse(return None)))
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
    case std@StdType(_, _) => Some((std.asClass(project.getOrElse(DecompilerUtil.obtainProject)).getOrElse(return None), ScSubstitutor.empty))
    case _ => None
  }

  def extractDesignated(t: ScType): Option[Pair[PsiNamedElement, ScSubstitutor]] = t match {
    case ScDesignatorType(e) => Some(e, ScSubstitutor.empty)
    case proj@ScProjectionType(p, e, s) => Some((proj.actualElement, proj.actualSubst))
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
  }
  })

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
          if (qname != null && qname != c.getName /* exlude default package*/ ) "_root_." + qname else c.getName
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
        buffer.append("(");
        appendSeq(params, ", ");
        buffer.append(") => ");
        inner(ret)
      }
      case ScThisType(clazz: ScTypeDefinition) => buffer.append(nameWithPointFun(clazz)).append("this.type")
      case ScThisType(clazz) => buffer.append("this.type")
      case ScTupleType(comps) => buffer.append("("); appendSeq(comps, ", "); buffer.append(")")
      case ScDesignatorType(e@(_: ScObject | _: ScReferencePattern)) => buffer.append(nameFun(e)).append(".type")
      case ScDesignatorType(e) => buffer.append(nameFun(e))
      case proj@ScProjectionType(p, el, su) => { //todo:
        val e = proj.actualElement
        val s = proj.actualSubst
        val refName = e.getName
        def appendPointType {
          e match {
            case obj: ScObject => buffer.append(".type")
            case v: ScBindingPattern => buffer.append(".type")
            case _ =>
          }
        }
        p match {
          case ScDesignatorType(pack: PsiPackage) => buffer.append(nameWithPointFun(pack)).append(refName)
          case ScDesignatorType(obj: ScObject) => {
            buffer.append(nameWithPointFun(obj)).append(refName)
            appendPointType
          }
          case ScDesignatorType(v: ScBindingPattern) => {
            buffer.append(nameWithPointFun(v)).append(refName)
            appendPointType
          }
          case ScThisType(obj: ScObject) => {
            buffer.append(nameWithPointFun(obj)).append(refName)
            appendPointType
          }
          case ScDesignatorType(clazz: PsiClass) if clazz.getLanguage != ScalaFileType.SCALA_LANGUAGE &&
                  e.isInstanceOf[PsiModifierListOwner] &&
                  e.asInstanceOf[PsiModifierListOwner].getModifierList.hasModifierProperty("static") => {
            buffer.append(nameWithPointFun(clazz)).append(refName)
          }
          case _ => inner(p); buffer.append("#").append(refName)
        }
      }
      case p: ScParameterizedType if p.getTupleType != None => inner(p.getTupleType.get)
      case p: ScParameterizedType if p.getFunctionType != None => inner(p.getFunctionType.get)
      case ScParameterizedType(des, typeArgs) => {
        inner(des);
        buffer.append("[");
        appendSeq(typeArgs, ", ");
        buffer.append("]")
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
      case c@ScCompoundType(comps, decls, typeDecls, s) => {
        def typeText0(tp: ScType) = typeText(s.subst(tp), nameFun, nameWithPointFun)
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
                  ScalaDocumentationProvider.parseParameter(param, typeText0)).
                        mkString("(", ", ", ")")).mkString(""))
                for (tp <- fun.returnType) {
                  val scType: ScType = s.subst(tp)
                  val text = if (!c.equiv(scType)) typeText(scType, nameFun, nameWithPointFun) else "this.type"
                    buffer.append(": ").append(text)
                }
                buffer.toString
              }
              case v: ScValue => {
                v.declaredElements.map(td => {
                  val scType: ScType = td.getType(TypingContext.empty).getOrElse(Any)
                  val text = if (!c.equiv(scType)) typeText0(scType) else "this.type"
                  "val " + td.name + ": " + text
                }).mkString("; ")
              }
              case v: ScVariable => {
                v.declaredElements.map(td => {
                  val scType: ScType = td.getType(TypingContext.empty).getOrElse(Any)
                  val text = if (!c.equiv(scType)) typeText0(scType) else "this.type"
                  "var " + td.name + ": " + text
                }).mkString("; ")
              }
              case _ => ""
            }
          }.mkString("; "))

          if (!typeDecls.isEmpty) {
            if (!decls.isEmpty) buffer.append("; ")
            buffer.append(typeDecls.map{
              (ta: ScTypeAlias) =>
              val paramsText = if (ta.typeParameters.length > 0)
                ta.typeParameters.map(param => typeParamText(param, s, nameFun, nameWithPointFun)).mkString("[", ", ", "]")
              else ""

              val decl = "type " + ta.getName + paramsText

              val defnText = ta match {
                case tad: ScTypeAliasDefinition =>
                  var x = ""
                  tad.aliasedType.foreach {
                    case psi.types.Nothing => ""
                    case tpe => x += (" = " + typeText0(tpe))
                  }
                  x
                case _ =>
                  var x = ""
                  ta.lowerBound foreach {
                    case psi.types.Nothing =>
                    case tp: ScType => x += (" >: " + typeText0(tp))
                  }
                  ta.upperBound foreach {
                    case psi.types.Any =>
                    case tp: ScType => x += (" <: " + typeText0(tp))
                  }
                  x
              }
              decl + defnText
            }.mkString("; "))
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

  private def typeParamText(param: ScTypeParam, subst: ScSubstitutor, nameFun: PsiNamedElement => String, nameWithPointFun: PsiNamedElement => String): String = {
    def typeText0(tp: ScType) = typeText(subst.subst(tp), nameFun, nameWithPointFun)
    var paramText = param.getName
    if (param.isContravariant) paramText = "-" + paramText
    else if (param.isCovariant) paramText = "+" + paramText
    param.lowerBound foreach {
      case psi.types.Nothing =>
      case tp: ScType => paramText += (" >: " + typeText0(tp))
    }
    param.upperBound foreach {
      case psi.types.Any =>
      case tp: ScType => paramText += (" <: " + typeText0(tp))
    }
    param.viewBound foreach {
      (tp: ScType) => paramText += (" <% " + typeText0(tp))
    }
    param.contextBound foreach {
      (tp: ScType) =>
        paramText += (" : " + typeText0(ScTypeUtil.stripTypeArgs(subst.subst(tp))))
    }
    paramText
  }

  // TODO: Review this against SLS 3.2.1
  def isStable(t: ScType): Boolean = {
    t match {
      case ScThisType(_) => true
      case ScProjectionType(projected, element: ScObject, _) => isStable(projected)
      case ScProjectionType(projected, element: ScTypedDefinition, _) => isStable(projected) && element.isStable
      case ScDesignatorType(o: ScObject) => true
      case ScDesignatorType(r: ScTypedDefinition) if r.isStable => true
      case _ => false
    }
  }
}
