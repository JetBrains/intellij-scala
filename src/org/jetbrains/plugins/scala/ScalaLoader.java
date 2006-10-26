package org.jetbrains.plugins.scala;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Author: Ilya Sergey
 * Date: 20.09.2006
 * Time: 16:31:20
 */
public class ScalaLoader implements ApplicationComponent {

    public void initComponent() {
        ApplicationManager.getApplication().runWriteAction(
                new Runnable() {
                    public void run() {
                        FileTypeManager.getInstance().registerFileType(ScalaFileType.SCALA_FILE_TYPE, new String[]{"scala"});
                    }
                }
        );
    }

    public void disposeComponent() {
    }

    @NotNull
    public String getComponentName() {
        return "Scala Loader";
    }
}