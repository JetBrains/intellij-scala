package org.jetbrains.plugins.scala;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.compiler.CompilerManager;
import org.jetbrains.plugins.scala.compiler.ScalaCompiler;
import org.jetbrains.annotations.NotNull;

/**
 * User: Dmitry.Krasilschikov
 * Date: 28.09.2006
 * Time: 17:15:51
 */
public class ScalaProject implements ProjectComponent {
    Project scalaProject;

    public ScalaProject(Project project) {
        this.scalaProject = project;
    }

    public void initComponent() {
        CompilerManager.getInstance(scalaProject).addCompiler(new ScalaCompiler());

        // TODO: insert component initialization logic here
    }

    public void disposeComponent() {
        // TODO: insert component disposal logic here
    }

    @NotNull
    public String getComponentName() {
        return "ScalaProject";
    }

    public void projectOpened() {
        // called when project is opened
    }

    public void projectClosed() {
        // called when project is being closed
    }


    public Project getScalaProject() {
        return scalaProject;
    }
}
