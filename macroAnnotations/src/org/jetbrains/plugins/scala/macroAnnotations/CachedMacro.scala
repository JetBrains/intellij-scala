package org.jetbrains.plugins.scala.macroAnnotations

import scala.annotation.tailrec
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 9/22/15.
 */
object CachedMacro {
  val debug: Boolean = false

  def cachedImpl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import c.universe._

    def abort(message: String) = c.abort(c.enclosingPosition, message)

    def parameters: (Boolean, ModCount.Value, Tree) = {
      @tailrec
      def modCountParam(modCount: c.universe.Tree): ModCount.Value = modCount match {
        case q"modificationCount = $v" => modCountParam(v)
        case q"ModCount.$v" => ModCount.withName(v.toString)
        case q"$v" => ModCount.withName(v.toString)
      }

      c.prefix.tree match {
        case q"new Cached(..$params)" if params.length == 3 =>
          val synch: Boolean = params.head match {
            case q"synchronized = $v" => c.eval[Boolean](c.Expr(v))
            case q"$v" => c.eval[Boolean](c.Expr(v))
          }
          val modCount: ModCount.Value = modCountParam(params(1))
          val manager = params(2)
          (synch, modCount, manager)
        case _ => abort("Wrong parameters")
      }
    }


    //todo: caching two overloaded functions
    annottees.toList match {
      case d@DefDef(mods, name, tpParams, paramss, retTp, rhs) :: Nil =>
        if (retTp.isEmpty) {
          abort("You must specify return type")
        }

        val (synchronized, modCount, manager) = parameters
        val flatParams = paramss.flatten
        val paramNames = flatParams.map(_.name)
        val cacheVarName = c.freshName(name)
        val modCountVarName = c.freshName(name)
        val mapName = c.freshName(name)
        val hasParameters: Boolean = flatParams.nonEmpty
        val cachedFunName = c.freshName("cachedFun")

        val fields = if (hasParameters) {
          q"""
            private val $mapName = _root_.com.intellij.util.containers.ContainerUtil.
                newConcurrentMap[(..${flatParams.map(_.tpt)}), ($retTp, _root_.scala.Long)]()
          """
        } else {
          q"""
            new _root_.scala.volatile()
            private var $cacheVarName: _root_.scala.Option[$retTp] = _root_.scala.None
            new _root_.scala.volatile()
            private var $modCountVarName: _root_.scala.Long = 0L
          """
        }

        def getValuesFromMap: c.universe.Tree = q"""
            var ($cacheVarName, $modCountVarName) = _root_.scala.Option($mapName.get(..$paramNames)) match {
              case _root_.scala.Some((res, count)) => (_root_.scala.Some(res), count)
              case _ => (_root_.scala.None, 0L)
            }
          """
        def putValuesIntoMap: c.universe.Tree = q"$mapName.put((..$paramNames), ($cacheVarName.get, $modCountVarName))"

        val functionContents = q"""
            val modCount = $manager.getModificationTracker.${TermName(modCount.toString)}
            ..${if (hasParameters) getValuesFromMap else EmptyTree}
            if ($cacheVarName.isDefined && $modCountVarName == modCount) $cacheVarName.get
            else {
              $cacheVarName = _root_.scala.Some($cachedFunName())
              $modCountVarName = modCount
              ..${if (hasParameters) putValuesIntoMap else EmptyTree}
              $cacheVarName.get
            }
          """
        val res = q"""
          ..$fields
          $mods def $name[..$tpParams](...$paramss): $retTp = {
            def $cachedFunName(): $retTp = $rhs

            ..${ if (synchronized) q"synchronized { $functionContents }" else functionContents }
          }
          """
        println(res)
        c.Expr(res)
      case _ => abort("You can only annotate one function!")
    }
  }

  def cachedMappedWithRecursionGuardImpl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import c.universe._
    def abort(s: String) = c.abort(c.enclosingPosition, s)

    annottees.toList match {
      case d@DefDef(mods, name, tpParams, paramss, retTp, rhs) :: Nil =>
        if (retTp.isEmpty) {
          abort("You must specify return type")
        }

        val (annotationParameters: List[Tree], dom) = c.prefix.tree match {
          case q"new CachedMappedWithRecursionGuard(..$params)" if params.length == 4 => (params, tq"com.intellij.psi.PsiElement")
          case _ => abort("Wrong annotation parameters!")
        }
        val flatParams = paramss.flatten
        val parameterTypes = flatParams.map(_.tpt)
        val parameterNames: List[c.universe.TermName] = flatParams.map(_.name)
        val cachesUtilFQN = q"_root_.org.jetbrains.plugins.scala.caches.CachesUtil"
        val key = annotationParameters(1)
        val cachedFunName = c.freshName("cachedFun")
        val dataName = c.freshName("data")
        val dataTypeName = c.freshName("Data")
        val parameterDefinitions: List[c.universe.Tree] = flatParams match {
          case List(param) => List(ValDef(NoMods, param.name, param.tpt, q"$dataName"))
          case _ => flatParams.zipWithIndex.map {
            case (param, i) =>
              ValDef(NoMods, param.name, param.tpt, q"$dataName.${TermName("_" + (i + 1))}")
          }
        }

        val builder = q"""
          def $cachedFunName(${c.freshName()}: _root_.scala.Any, $dataName: $dataTypeName): $retTp = {
            ..$parameterDefinitions
            $rhs
          }
          """
        val element = annotationParameters.head
        val defaultValue = annotationParameters(2)
        val dependencyItem = annotationParameters(3)

        val updatedRhs = q"""
          type $dataTypeName = (..$parameterTypes)
          val $dataName = (..$parameterNames)

          $builder

          $cachesUtilFQN.getMappedWithRecursionPreventingWithRollback[$dom, $dataTypeName, $retTp]($element, $dataName, $key, $cachedFunName, $defaultValue, $dependencyItem)
          """
        val res = c.Expr(DefDef(mods, name, tpParams, paramss, retTp, updatedRhs))
        println(res)
        res
      case _ => abort("You can only annotate one function!")
    }
  }

  def cachedWithRecursionGuardImpl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import c.universe._
    def abort(s: String) = c.abort(c.enclosingPosition, s)

    annottees.toList match {
      case d@DefDef(mods, name, tpParams, params, retTp, rhs) :: Nil =>
        if (retTp.isEmpty) {
          abort("You must specify return type")
        }

        val cachesUtilFQN = q"_root_.org.jetbrains.plugins.scala.caches.CachesUtil"

        val (annotationParameters: List[Tree], providerType, useOptionalProvider: Boolean) = c.prefix.tree match {
          case q"new CachedWithRecursionGuard[$t](..$params)" if params.length == 4 => (params, t, false) //default parameter
          case q"new CachedWithRecursionGuard[$t](..$params)" if params.length == 5 =>
            val optional = params.last match {
              case q"useOptionalProvider = $v" => c.eval[Boolean](c.Expr(v))
              case q"$v" => c.eval[Boolean](c.Expr(v))
            }
            (params, t, optional)
          case _ => abort("Wrong annotation parameters!")
        }
        val element = annotationParameters.head
        val key = annotationParameters(1)
        val defaultValue = annotationParameters(2)
        val dependencyItem = annotationParameters(3)
        val cachedFunName = c.freshName("cachedFun")
        val provider =
          if (useOptionalProvider) TypeName("MyOptionalProvider")
          else TypeName("MyProvider")
        val fun = q"def $cachedFunName(): $retTp = $rhs"
        val builder = q"new $cachesUtilFQN.$provider[$providerType, $retTp]($element, _ => $cachedFunName())($dependencyItem)"
        val updatedRhs = q"""
          $fun
          $cachesUtilFQN.getWithRecursionPreventingWithRollback($element, $key, $builder, $defaultValue)
          """
        val res = DefDef(mods, name, tpParams, params, retTp, updatedRhs)
        println(res)
        c.Expr(res)
      case _ => abort("You can only annotate one function!")
    }
  }

  def cachedInsidePsiElementImpl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import c.universe._
    def abort(s: String) = c.abort(c.enclosingPosition, s)

    annottees.toList match {
      case d@DefDef(mods, name, tpParams, paramss, retTp, rhs) :: Nil =>
        if (retTp.isEmpty) {
          abort("You must specify return type")
        }
        val (annotationParameters: List[Tree], useOptionalProvider: Boolean) = c.prefix.tree match {
          case q"new CachedInsidePsiElement(..$params)" if params.length == 3 => (params, false)
          case q"new CachedInsidePsiElement(..$params)" if params.length == 4 =>
            val optional = params.last match {
              case q"useOptionalProvider = $v" => c.eval[Boolean](c.Expr(v))
              case q"$v" => c.eval[Boolean](c.Expr(v))
            }
            (params, optional)
          case _ => abort("Wrong annotation parameters!")
        }
        val cachesUtilFQN = q"_root_.org.jetbrains.plugins.scala.caches.CachesUtil"
        val elem = annotationParameters.head
        val key = annotationParameters(1)
        val dependencyItem = annotationParameters(2)
        val cachedFunName = c.freshName("cachedFun")
        val provider =
          if (useOptionalProvider) TypeName("MyOptionalProvider")
          else TypeName("MyProvider")
        val updatedRhs = q"""
          def $cachedFunName(): $retTp = $rhs

          $cachesUtilFQN.get($elem, $key, new $cachesUtilFQN.$provider[Any, $retTp]($elem, _ => $cachedFunName())($dependencyItem))
          """
        val res = DefDef(mods, name, tpParams, paramss, retTp, updatedRhs)
        println(res)
        c.Expr(res)
      case _ => abort("You can only annotate one function!")
    }
  }

  def println(a: Any): Unit = {
    if (debug) {
      Console.println(a)
    }
  }
}

object ModCount extends Enumeration {
  type ModCount = Value
  val getModificationCount = Value("getModificationCount")
  val getOutOfCodeBlockModificationCount = Value("getOutOfCodeBlockModificationCount")
  val getJavaStructureModificationCount = Value("getJavaStructureModificationCount")
}
