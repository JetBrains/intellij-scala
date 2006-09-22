package org.jetbrains.plugins.scala;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.application.ApplicationManager;

/**
 * Author: Ilya Sergey
 * Date: 20.09.2006
 * Time: 16:31:20
 */
public class ScalaLoader implements ApplicationComponent {

    public static final LanguageFileType SCALA = new ScalaFileType();

    public ScalaLoader() {
    }

    public void initComponent() {
        ApplicationManager.getApplication().runWriteAction(
            new Runnable() {
                public void run() {
                    FileTypeManager.getInstance().registerFileType(SCALA, new String[] {"scala"});
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
