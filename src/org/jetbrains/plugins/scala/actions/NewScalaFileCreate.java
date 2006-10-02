package org.jetbrains.plugins.scala.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.IconLoader;

/**
 * Author: Ilya Sergey
 * Date: 24.09.2006
 * Time: 18:03:32
 */
public class NewScalaFileCreate extends AnAction {

    public NewScalaFileCreate(){
        super("Scala file",
              "Create new Scala file", 
              IconLoader.getIcon("/org/jetbrains/plugins/scala/images/scala_logo.png"));
    }

    public void actionPerformed(AnActionEvent e) {
        
    }
}
