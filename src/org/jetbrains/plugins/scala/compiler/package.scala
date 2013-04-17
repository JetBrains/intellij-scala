package org.jetbrains.plugins.scala

import java.io.File
import com.intellij.openapi.projectRoots.{SdkType, JavaSdkType, ProjectJdkTable}

/**
 * @author Pavel Fatin
 */
package object compiler {
  def javaExecutableIn(sdkName: String): Either[String, File] = {
    val javaPath = {
      Option(sdkName).toRight("No JVM SDK configured").right.flatMap { name =>

        val projectSdk = Option(ProjectJdkTable.getInstance().findJdk(name))
                .toRight("JVM SDK does not exists: " + sdkName)

        projectSdk.right.flatMap { sdk =>
          sdk.getSdkType match {
            case sdkType: SdkType with JavaSdkType =>
              Either.cond(sdkType.sdkHasValidPath(sdk),
                sdkType.getVMExecutablePath(sdk), "Not valid SDK path: " + sdkName)
            case _ => Left("Not a Java SDK: " + sdkName)
          }
        }
      }
    }

    javaPath.right.flatMap { path =>
      val file = new File(path)
//      Either.cond(file.exists, file, "Java executable in JVM SDK does not exist: " + file.getPath)
      Right(file)
    }
  }
}
