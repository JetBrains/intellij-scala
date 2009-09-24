package org.jetbrains.plugins.scala
package lang
package psi
package types

import api.base.{ScStableCodeReferenceElement}
import api.statements.{ScTypeAliasDefinition}
import api.toplevel.ScNamedElement
import api.toplevel.typedef.{ScClass, ScObject}
import com.intellij.psi.util.PsiTypesUtil
import decompiler.DecompilerUtil
import impl.ScalaPsiManager
import impl.toplevel.synthetic.{SyntheticClasses, ScSyntheticClass}
import resolve.ScalaResolveResult
import com.intellij.psi._
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import api.expr.{ScSuperReference, ScThisReference}

trait ScType {
  def equiv(t: ScType): Boolean = t == this

  def conforms(t: ScType): Boolean = Conformance.conforms(t, this)

  override def toString = ScType.presentableText(this)

  def parts: Seq[ScType] = Seq(this)
}

abstract case class StdType(val name : String, val tSuper : Option[StdType]) extends ScType {
  def asClass(project : Project) = SyntheticClasses.get(project).byName(name).get
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
object Short extends ValType("Float")

object ScType {
  def create(psiType: PsiType, project: Project): ScType = psiType match {
    case classType: PsiClassType => {
      val result = classType.resolveGenerics
      result.getElement match {
        case tp: PsiTypeParameter => ScalaPsiManager.typeVariable(tp)
        case clazz if clazz != null => {
          val tps = clazz.getTypeParameters
          val des = new ScDesignatorType(clazz)
          tps match {
            case Array() => des
            case _ => new ScParameterizedType(des, Seq(tps.map
                      {tp => create(result.getSubstitutor.substitute(tp), project)} : _*))
          }
        }
        case _ => Nothing
      }
    }
    case arrayType: PsiArrayType => {
      val arrayClasses = JavaPsiFacade.getInstance(project).findClasses("scala.Array", GlobalSearchScope.allScope(project))
      var arrayClass: PsiClass = null
      for (clazz <- arrayClasses) {
        clazz match {
          case _: ScClass => arrayClass = clazz
          case _ =>
        }
      }
      if (arrayClass != null) {
        val tps = arrayClass.getTypeParameters
        if (tps.length == 1) {
          val typeArg = create(arrayType.getComponentType, project)
          new ScParameterizedType(new ScDesignatorType(arrayClass), Seq(typeArg))
        } else new ScDesignatorType(arrayClass)
      } else Nothing
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
      if(wild.isSuper) create(wild.getSuperBound, project) else Nothing,
      if(wild.isExtends) create(wild.getExtendsBound, project) else Any)
    case capture : PsiCapturedWildcardType =>
      val wild = capture.getWildcard
      new ScSkolemizedType("_", Nil,
        if(wild.isSuper) create(capture.getLowerBound, project) else Nothing,
        if(wild.isExtends) create(capture.getUpperBound, project) else Any)
    case null => new ScExistentialArgument("_", Nil, Nothing, Any) // raw type argument from java
    case _ => throw new IllegalArgumentException("psi type " + psiType + " should not be converted to scala type")
  }

  def toPsi(t: ScType, project: Project, scope: GlobalSearchScope): PsiType = {
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
      //todo add generics
      case ScFunctionType(rt, args) => {
        val facade = JavaPsiFacade.getInstance(project)
        val c: PsiClass = facade.findClass("scala.Function" + (args.length))
        if (c == null) return javaObj
        val typeParams = c.getTypeParameters
        val subst = (args ++ Seq(rt)).toArray[ScType].zip(c.getTypeParameters).foldLeft(PsiSubstitutor.EMPTY){
          case (s, (targ, tp)) => {
            val str = toPsi(targ, project, scope).getPresentableText
            val boxed = PsiTypesUtil.boxIfPossible(str)
            s.put(tp, facade.getElementFactory.createTypeByFQClassName(boxed, scope))
          }
        }
        facade.getElementFactory.createType(c, subst)
      }
      case ScTupleType(args) => {
        val facade = JavaPsiFacade.getInstance(project)
        val c: PsiClass = facade.findClass("scala.Tuple" + (args.length))
        if (c == null) return javaObj
        val typeParams = c.getTypeParameters
        val subst = args.toArray[ScType].zip(c.getTypeParameters).foldLeft(PsiSubstitutor.EMPTY){
          case (s, (targ, tp)) => {
            val str = toPsi(targ, project, scope).getPresentableText
            val boxed = PsiTypesUtil.boxIfPossible(str)
            s.put(tp, facade.getElementFactory.createTypeByFQClassName(boxed, scope))
          }
        }
        facade.getElementFactory.createType(c, subst)
      }
      case ScCompoundType(Seq(t, _*), _, _) => toPsi(t, project, scope)
      case ScDesignatorType(c: PsiClass) => JavaPsiFacade.getInstance(project).getElementFactory.createType(c, PsiSubstitutor.EMPTY)
      case ScParameterizedType(ScDesignatorType(c: PsiClass), args) =>
        if (c.getQualifiedName == "scala.Array" && args.length == 1)
          new PsiArrayType(toPsi(args(0), project, scope))
        else {
          val subst = args.zip(c.getTypeParameters).foldLeft(PsiSubstitutor.EMPTY)
                    {case (s, (targ, tp)) => s.put(tp, toPsi(targ, project, scope))}
          JavaPsiFacade.getInstance(project).getElementFactory.createType(c, subst)
        }
      case ScParameterizedType(ScProjectionType(pr, ref), args) => ref.bind.map(_.getElement) match {
        case Some(c: PsiClass) => if (c.getQualifiedName == "scala.Array" && args.length == 1)
          new PsiArrayType(toPsi(args(0), project, scope))
        else {
          val subst = args.zip(c.getTypeParameters).foldLeft(PsiSubstitutor.EMPTY)
                    {case (s, (targ, tp)) => s.put(tp, toPsi(targ, project, scope))}
          JavaPsiFacade.getInstance(project).getElementFactory.createType(c, subst)
        }
        case _ => javaObj
      }

      case ScProjectionType(pr, ref) => ref.bind match {
        case Some(result) if result.isCyclicReference => javaObj
        case Some(result) if result.getElement.isInstanceOf[PsiClass] => {
          val clazz = result.getElement.asInstanceOf[PsiClass]
          clazz match {
            case syn: ScSyntheticClass => toPsi(syn.t, project, scope)
            case _ => {
              val fqn = clazz.getQualifiedName
              JavaPsiFacade.getInstance(project).getElementFactory.createTypeByFQClassName(if (fqn != null) fqn else "java.lang.Object", scope)
            }
          }
        }
        case Some(res) if res.getElement.isInstanceOf[ScTypeAliasDefinition] =>
          toPsi(res.getElement.asInstanceOf[ScTypeAliasDefinition].aliasedType(Set[ScNamedElement]()).resType, project, scope)
        case _ => javaObj
      }
      case _ => javaObj
    }
  }

  def extractClassType(t: ScType): Option[Pair[PsiClass, ScSubstitutor]] = t match {
    case ScDesignatorType(clazz: PsiClass) => Some(clazz, ScSubstitutor.empty)
    case proj@ScProjectionType(p, _) => proj.resolveResult match {
      case Some(ScalaResolveResult(c: PsiClass, s)) => Some((c, s))
      case _ => None
    }
    case p@ScParameterizedType(t1, _) => {
      extractClassType(t1) match {
        case Some((c, s)) => Some((c, s.followed(p.substitutor)))
        case None => None
      }
    }
    case std@StdType(_, _) => Some((std.asClass(DecompilerUtil.obtainProject), ScSubstitutor.empty))
    case _ => None
  }

  def extractDesignated(t: ScType): Option[Pair[PsiNamedElement, ScSubstitutor]] = t match {
    case ScDesignatorType(e) => Some(e, ScSubstitutor.empty)
    case proj@ScProjectionType(p, _) => proj.resolveResult match {
      case Some(ScalaResolveResult(e, s)) => Some((e, s))
      case _ => None
    }
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
        case e: PsiClass => "<a href=\"psi_element://" + e.getQualifiedName + "\"><code>" + e.getName + "</code></a>."
        case pack: PsiPackage if withPoint => ""
        case _ => e.getName + "."
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
          if (qname != null) "_root_." + qname else c.getName
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
      case StdType(name, _) => buffer.append(name)
      case ScFunctionType(ret, params) => buffer.append("("); appendSeq(params, ", "); buffer.append(") => "); inner(ret)
      case ScTupleType(comps) => buffer.append("("); appendSeq(comps, ", "); buffer.append(")")
      case ScDesignatorType(e) => buffer.append(nameFun(e))
      case ScProjectionType(p, ref) => p match {
        case ScSingletonType(path: ScStableCodeReferenceElement) => path.bind match {
          case Some(res) => buffer.append(nameWithPointFun(res.getElement)).append(ref.refName)
          case None => inner(p); buffer.append(".").append(ref.refName)
        } //todo: another shorthands for ScSingletonType
        case _ => inner(p); buffer.append("#").append(ref.refName)
      }
      case ScParameterizedType(des, typeArgs) => inner(des); buffer.append("["); appendSeq(typeArgs, ","); buffer.append("]")
      case ScSkolemizedType(name, _, _, _) => buffer.append(name)
      case ScPolymorphicType(name, _, _, _) => buffer.append(name)
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
      case ScExistentialType(q, wilds) => {
        inner(q)
        buffer.append(" forSome{");
        appendSeq(wilds, "; ");
        buffer.append("}")
      }
      case ScSingletonType(path: ScStableCodeReferenceElement) => {
        path.bind match {
          case Some(r: ScalaResolveResult) => buffer.append(nameFun(r.getElement))
          case _ => buffer.append(path.qualName)
        }
        buffer.append(".type")
      }
      case ScSingletonType(path: ScThisReference) => {//todo: this can become super etc.
        path.refTemplate match {
          case Some(clazz: PsiClass) => {
            buffer.append(clazz.getName).append(".this")
          }
          case _ => {
            path.reference match {
              case Some(ref: ScStableCodeReferenceElement) => buffer.append(ref.refName).append(".this")
              case None => buffer.append("this")
            }
          }
        }
        buffer.append(".type")
      }
      case ScSingletonType(path: ScSuperReference) => {//todo: this can become super etc.
        path.staticSuper match {
          case Some(tp: ScType) => inner(tp)
          case None => {
            path.drvTemplate match {
              case Some(clazz: PsiClass) => {
                buffer.append(clazz.getName).append(".super")
              }
              case _ => {
                path.qualifier match {
                  case Some(ref: ScStableCodeReferenceElement) => buffer.append(ref.refName).append(".super")
                  case None => buffer.append("super")
                }
              }
            }
            buffer.append(".type")
          }
        }
      }
      case _ => null //todo
    }
    inner(t)
    buffer.toString
  }
}
