package org.jetbrains.plugins.scala;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.application.ApplicationManager;

/**
 * Author: Ilya Sergey
 * Date: 20.09.2006
 * Time: 16:31:20
 */
public class ScalaLoader implements ApplicationComponent {

    public ScalaLoader() {
    }

    public void initComponent() {
        ApplicationManager.getApplication().runWriteAction(
            new Runnable() {
                public void run() {
                    FileTypeManager.getInstance().registerFileType(ScalaFileType.SCALA_FILE_TYPE, new String[] {"scala"});
                }
            }
        );
     }

    public void disposeComponent() {
    }

    public String getComponentName() {
        return "ScalaLoader";
    }
}
