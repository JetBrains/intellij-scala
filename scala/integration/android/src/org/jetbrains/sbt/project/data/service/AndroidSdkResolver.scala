package org.jetbrains.sbt.project.data.service


import com.android.tools.sdk.AndroidPlatform
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import org.jetbrains.android.sdk.{AndroidPlatforms, AndroidSdkType}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.external.{AndroidJdk, SdkReference, SdkResolver}

import scala.jdk.CollectionConverters._

class AndroidSdkResolver extends SdkResolver {
  override def findSdk(reference: SdkReference): Option[Sdk] = reference match {
    case AndroidJdk(version) => AndroidSdkResolver.findAndroidJdkByVersion(version)
    case _ => None
  }
}

private object AndroidSdkResolver {
  private def findAndroidJdkByVersion(version: String): Option[Sdk] = {
    def isGEQAsInt(fst: String, snd: String): Boolean =
      try {
        val fstInt = fst.toInt
        val sndInt = snd.toInt
        fstInt >= sndInt
      } catch {
        case _: NumberFormatException => false
      }

    val matchingSdks = for {
      sdk <- allAndroidSdks
      platform <- Option(AndroidPlatforms.getInstance(sdk))
      platformVersion = platform.getApiLevel.toString
      if isGEQAsInt(platformVersion, version)
    } yield sdk

    matchingSdks.headOption
  }

  private def allAndroidSdks: Seq[Sdk] = inReadAction {
    ProjectJdkTable.getInstance().getSdksOfType(AndroidSdkType.getInstance()).asScala.toSeq
  }
}
