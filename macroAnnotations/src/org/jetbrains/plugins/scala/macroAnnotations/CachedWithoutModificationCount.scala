package org.jetbrains.plugins.scala.macroAnnotations

import org.jetbrains.plugins.scala.macroAnnotations.ValueWrapper.ValueWrapper

import scala.annotation.{StaticAnnotation, tailrec}
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
  * If you annotate a function with @CachedWithoutModificationCount annotation, the compiler will generate code to cache it.
  *
  * If an annotated function has parameters, one field will be generated (a HashMap).
  * If an annotated function has no parameters, two fields will be generated: result and modCount
  *
  * NOTE !IMPORTANT!: function annotated with @Cached must be on top-most level because generated code generates fields
  * right outside the cached function and if this function is inner it won't work.
  *
  * NOTE: Caching overloaded functions is currently not supported!
  * Author: Svyatoslav Ilinskiy
  * Date: 10/20/15.
  */
class CachedWithoutModificationCount(synchronized: Boolean, valueWrapper: ValueWrapper) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro CachedWithoutModificationCount.cachedWithoutModificationCountImpl
}

object CachedWithoutModificationCount {
  val cachedMapPostfix: String = "$cachedMap"

  def cachedWithoutModificationCountImpl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import CachedMacroUtil._
    import c.universe._
    implicit val x: c.type = c

    val analyzeCaches = analyzeCachesEnabled(c)

    def parameters: (Boolean, ValueWrapper) = {
      @tailrec
      def valueWrapperParam(valueWrapper: Tree): ValueWrapper = valueWrapper match {
        case q"valueWrapper = $v" => valueWrapperParam(v)
        case q"ValueWrapper.$v" => ValueWrapper.withName(v.toString)
        case q"$v" => ValueWrapper.withName(v.toString)
      }

      c.prefix.tree match {
        case q"new CachedWithoutModificationCount(..$params)" if params.length == 2 =>
          val synch: Boolean = params.head match {
            case q"synchronized = $v" => c.eval[Boolean](c.Expr(v))
            case q"$v" => c.eval[Boolean](c.Expr(v))
          }
          val valueWrapper = valueWrapperParam(params(1))
          (synch, valueWrapper)
        case _ => abort("Wrong parameters")
      }
    }

    //annotation parameters
    val (synchronized, valueWrapper) = parameters

    annottees.toList match {
      case DefDef(mods, name, tpParams, paramss, retTp, rhs) :: Nil =>
        if (retTp.isEmpty) {
          abort("You must specify return type")
        }
        //generated names
        val cacheVarName = c.freshName(name)
        val mapName = TermName(name + cachedMapPostfix)
        val cachedFunName = generateTermName("cachedFun")
        val cacheStatsName = generateTermName(name + "cacheStats")
        val keyId = c.freshName(name.toString + "cacheKey")
        val defdefFQN = thisFunctionFQN(name.toString)

        //DefDef parameters
        val flatParams = paramss.flatten
        val paramNames = flatParams.map(_.name)
        val hasParameters: Boolean = flatParams.nonEmpty

        val analyzeCachesField =
          if (analyzeCaches) {
            val cacheDecl = q"private val $cacheStatsName = $cacheStatisticsFQN($keyId, $defdefFQN)"
            if (hasParameters) {
              //need to put map in cacheStats, so its size can be measured
              q"""
                $cacheDecl
                $cacheStatsName.addCacheObject($mapName)
              """
            } else cacheDecl
          } else EmptyTree
        val wrappedRetTp: Tree = valueWrapper match {
          case ValueWrapper.None => retTp
          case ValueWrapper.WeakReference => tq"_root_.java.lang.ref.WeakReference[$retTp]"
          case ValueWrapper.SoftReference => tq"_root_.java.lang.ref.SoftReference[$retTp]"
          case ValueWrapper.SofterReference => tq"_root_.com.intellij.util.SofterReference[$retTp]"
        }
        val fields = if (hasParameters) {
          q"""
            private val $mapName = new java.util.concurrent.ConcurrentHashMap[(..${flatParams.map(_.tpt)}), $wrappedRetTp]()
            ..$analyzeCachesField
          """
        } else {
          q"""
            new _root_.scala.volatile()
            private var $cacheVarName: $wrappedRetTp = null.asInstanceOf[$wrappedRetTp]
            ..$analyzeCachesField
          """
        }

        def getValuesFromMap: c.universe.Tree =
          q"""
            var $cacheVarName = _root_.scala.Option($mapName.get(..$paramNames)).getOrElse(null.asInstanceOf[$wrappedRetTp])
          """
        def putValuesIntoMap: c.universe.Tree = q"$mapName.put((..$paramNames), $cacheVarName)"

        val hasCacheExpired =
          if (valueWrapper == ValueWrapper.None) q"$cacheVarName == null.asInstanceOf[$wrappedRetTp]"
          else {
            q"""
              $cacheVarName == null.asInstanceOf[$wrappedRetTp] || $cacheVarName.get() == null.asInstanceOf[$retTp]
            """
          }

        val wrappedResult = valueWrapper match {
          case ValueWrapper.None => q"cacheFunResult"
          case ValueWrapper.WeakReference => q"new _root_.java.lang.ref.WeakReference(cacheFunResult)"
          case ValueWrapper.SoftReference => q"new _root_.java.lang.ref.SoftReference(cacheFunResult)"
          case ValueWrapper.SofterReference => q"new _root_.com.intellij.util.SofterReference(cacheFunResult)"
        }

        val functionContents =
          q"""
            ${if (analyzeCaches) q"$cacheStatsName.aboutToEnterCachedArea()" else EmptyTree}
            ..${if (hasParameters) getValuesFromMap else EmptyTree}
            val cacheHasExpired = $hasCacheExpired
            if (cacheHasExpired) {
              val cacheFunResult = $cachedFunName()
              $cacheVarName = $wrappedResult
              ..${if (hasParameters) putValuesIntoMap else EmptyTree}
            }
            ${if (valueWrapper == ValueWrapper.None) q"$cacheVarName" else q"$cacheVarName.get"}
          """
        val getValuesIfHasParams =
          if (hasParameters) {
            q"""
              ..$getValuesFromMap
            """
          } else q""

        val functionContentsInSynchronizedBlock =
          if (synchronized) {
            q"""
              ..$getValuesIfHasParams
              if ($hasCacheExpired) {
                return $cacheVarName.get
              }
              synchronized {
                $functionContents
              }
            """
          } else {
            q"""
              $functionContents
            """
          }
        val actualCalculation = transformRhsToAnalyzeCaches(c)(cacheStatsName, retTp, rhs)
        val updatedRhs =
          q"""
          def $cachedFunName(): $retTp = {
            $actualCalculation
          }
          $functionContentsInSynchronizedBlock
        """
        val updatedDef = DefDef(mods, name, tpParams, paramss, retTp, updatedRhs)
        val res =
          q"""
          ..$fields
          $updatedDef
          """
        println(res)
        c.Expr(res)
      case _ => abort("You can only annotate one function!")
    }
  }
}

object ValueWrapper extends Enumeration {
  type ValueWrapper = Value
  val None = Value("None")
  val SoftReference = Value("SoftReference")
  val WeakReference = Value("WeakReference")
  val SofterReference = Value("SofterReference")
}