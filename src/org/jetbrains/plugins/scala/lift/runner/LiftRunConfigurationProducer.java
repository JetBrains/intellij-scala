package org.jetbrains.plugins.scala.lift.runner;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.plugins.scala.util.ScalaUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author ilyas
 */
public class LiftRunConfigurationProducer extends RunConfigurationProducer<MavenRunConfiguration> implements Cloneable {

  private static final String GROUP_ID_LIFT = "net.liftweb";
  private static final String ARTIFACT_ID_LIFT = "lift-webkit";

  private static final String JETTY_RUN = "jetty:run";

  @Override
  protected boolean setupConfigurationFromContext(MavenRunConfiguration configuration, ConfigurationContext context,
                                                  Ref sourceElement) {
    if (sourceElement.isNull()) return false;
    final Module module = context.getModule();
    if (module == null || !ScalaUtils.isSuitableModule(module)) return false;

    final MavenRunnerParameters params = createBuildParameters(context.getLocation());
    if (params == null) return false;
    configuration.setRunnerParameters(params);
    return true;
  }

  @Override
  public boolean isConfigurationFromContext(MavenRunConfiguration configuration, ConfigurationContext context) {
    return false;
  }

  public LiftRunConfigurationProducer() {
    super(ConfigurationTypeUtil.findConfigurationType(MavenRunConfigurationType.class));
  }

  private MavenRunnerParameters createBuildParameters(Location l) {
    final PsiElement element = l.getPsiElement();
    final Project project = l.getProject();

    final Module module = ModuleUtil.findModuleForPsiElement(element);
    if (module == null) return null;

    final MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(project);
    final MavenProject mavenProject = mavenProjectsManager.findProject(module);

    if (mavenProject == null) return null;

    //todo: check this code
    final List<MavenArtifact> dependencies = mavenProject.getDependencies();
    MavenArtifact artifact = null;
    for (MavenArtifact dependence : dependencies) {
      if (dependence.getArtifactId().equals(GROUP_ID_LIFT)) {
        artifact = dependence;
        break;
      } else if (dependence.getArtifactId().equals(ARTIFACT_ID_LIFT)) {
        artifact = dependence;
        break;
      }
    }

    if (artifact == null) return null;

    Collection<String> profiles = MavenProjectsManager.getInstance(project).getExplicitProfiles().getEnabledProfiles();
    List<String> goals = new ArrayList<String>();

    goals.add(JETTY_RUN);

    final VirtualFile file = module.getModuleFile();
    if (file == null) return null;

    final VirtualFile parent = file.getParent();
    if (parent  == null) return null;
    
    return new MavenRunnerParameters(true, parent.getPath(), goals, profiles);
  }
}
