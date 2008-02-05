package org.jetbrains.plugins.scala

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;

import com.intellij.CommonBundle;

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 05.02.2008
* Time: 12:48:28
* To change this template use File | Settings | File Templates.
*/

object ScalaBundleImpl{
  private var ourBundle: Reference[ResourceBundle] = null

  private val BUNDLE:String = "org.jetbrains.plugins.scala.ScalaBundle"

  def message(key: String, params: Object*): String = {
    return CommonBundle.message(getBundle(), key, params.toArray)
  }

  private def getBundle(): ResourceBundle = {
    var bundle: ResourceBundle = null

    if (ourBundle != null) bundle = ourBundle.get()

    if (bundle == null) {
      bundle = ResourceBundle.getBundle(BUNDLE);
      ourBundle = new SoftReference[ResourceBundle](bundle);
    }
    bundle;
  }
  def apply(key: String): String = {
    ScalaBundle.message(key, new Array[Object](0))
  }
}