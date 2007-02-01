package org.jetbrains.plugins.scala.cache.info;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiFile;

/**
 * @author Ilya.Sergey
 */
public class ScalaInfoFactory {

      /**
     * Creates new RFileInfo for given file
     * @param project Current project
     * @param file current file
     * @return RFileInfo object containing information about file
     * or null if file cannot be found or isn`t ruby file
     */
/*
    @Nullable

    public static RFileInfo createRFileInfo(@NotNull final Project project, @NotNull final VirtualFile file) {
        if (!file.isValid()) {
            return null;
        }
        final PsiManager myPsiManager = PsiManager.getInstance(project);
        final PsiFile psiFile = myPsiManager.findFile(file);
        if (psiFile == null || !(psiFile instanceof RFile)) {
            return null;
        }
        final String parentDirUrl = file.getParent() == null ? null : file.getParent().getUrl();
        if (parentDirUrl == null) {
            return null;
        }

// creating new RFileInfo
        final RFileInfo fileInfo = new RFileInfoImpl(file.getName(), parentDirUrl, file.getTimeStamp());
        final RVirtualContainer vc = RVirtualUtils.createBy((RFile) psiFile, fileInfo);
        fileInfo.setVirtualContainer(vc);
        return fileInfo;
    }
*/

}
