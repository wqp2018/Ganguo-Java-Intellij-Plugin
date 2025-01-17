package com.ganguo.java.plugin.action.menu;

import com.ganguo.java.plugin.action.BaseAnAction;
import com.ganguo.java.plugin.constant.Filenames;
import com.ganguo.java.plugin.constant.Paths;
import com.ganguo.java.plugin.ui.dialog.AddMsgDialog;
import com.ganguo.java.plugin.util.CopyPasteUtils;
import com.ganguo.java.plugin.util.FileUtils;
import com.ganguo.java.plugin.util.IndexUtils;
import com.ganguo.java.plugin.util.MsgUtils;
import com.ganguo.java.plugin.util.NotificationHelper;
import com.ganguo.java.plugin.util.PsiUtils;
import com.ganguo.java.plugin.util.SafeProperties;
import com.ganguo.java.plugin.util.WriteActions;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiParserFacade;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import lombok.extern.slf4j.Slf4j;
import org.dependcode.dependcode.Context;
import org.dependcode.dependcode.ContextBuilder;
import org.dependcode.dependcode.anno.Func;
import org.dependcode.dependcode.anno.Var;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * 添加ExceptionMsg
 */
@Slf4j
public class AddExceptionMsgAction extends BaseAnAction {

    @Override
    public void action(AnActionEvent e) {
        new AddMsgDialog(e, this::doAction).show();
    }

    private boolean doAction(AnActionEvent event, String key, String value) {
        Context context = ContextBuilder.of(this)
                .put("event", event)
                .put("key", key)
                .put("value", value)
                .build();

        Status status = context.exec("add2Properties", Status.class).get();
        if (status == Status.FAIL) return false;
        if (status == Status.EXISTS) return true;

        status = context.exec("add2Class", Status.class).get();
        if (status == Status.FAIL) return false;

        context.execVoid("paste");
        return true;
    }

    /**
     * exception_msg.properties文件
     */
    @Var
    private VirtualFile msgFile(VirtualFile rootFile) {
        VirtualFile file = rootFile.findFileByRelativePath(Paths.MSG_PROPERTIES);
        if (file == null) {
            file = rootFile.findFileByRelativePath(Paths.MSG_ZH_PROPERTIES);
        }
        if (file == null) {
            file = rootFile.findFileByRelativePath(Paths.MSG_ZH_CN_PROPERTIES);
        }
        if (file == null) {
            file = rootFile.findFileByRelativePath(Paths.MSG_EN_PROPERTIES);
        }
        return file;
    }

    /**
     * msg的properties对象
     */
    @Var
    private SafeProperties properties(VirtualFile msgFile) {
        return ApplicationManager.getApplication().runReadAction((Computable<SafeProperties>) () -> {
            SafeProperties properties = new SafeProperties();
            try {
                properties.load(msgFile.getInputStream());
            } catch (IOException e) {
                log.error("read {} fail", Paths.MSG_PROPERTIES, e);
            }
            return properties;
        });
    }

    /**
     * 添加到exception_msg.properties中
     */
    @Func
    private Status add2Properties(VirtualFile msgFile, SafeProperties properties, String key, String value,
                                  WriteActions writeActions) {
        // 检查Value是否已存在
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            if (value.equals(entry.getValue())) {
                CopyPasteUtils.putString(entry.getValue().toString());
                CopyPasteUtils.putString(entry.getKey().toString());

                NotificationHelper.info("发现%s已存在，已放入粘贴板", entry.getValue()).show();

                return Status.EXISTS;
            }
        }

        // 检查Key是否已存在
        if (properties.containsKey(key)) {
            int result = Messages.showYesNoDialog(key + "已存在，是否覆盖？",
                    "提示", "覆盖", "取消", null);
            if (result != Messages.YES) {
                CopyPasteUtils.putString(key);
                return Status.FAIL;
            }
        }

        properties.setProperty(key, value);
        writeActions.add(() -> {
            try {
                FileUtils.setContent(msgFile, properties);
            } catch (IOException e) {
                log.error("write {} fail", Paths.MSG_PROPERTIES, e);
            }
        }).run();

        return Status.SUCCESS;
    }

    /**
     * ExceptionMsg.java的Class文件对象
     */
    @Var
    private PsiClass msgClass(Project project) {
        return Arrays.stream(IndexUtils.getFilesByName(project, Filenames.MSG_CLASS))
                .findFirst()
                .map(file -> PsiTreeUtil.findChildOfType(file, PsiClass.class))
                .orElse(null);
    }

    /**
     * 添加到到ExceptionMsg.java中
     */
    @Func
    private Status add2Class(Project project, PsiClass msgClass, PsiElementFactory elementFactory,
                             String key, String value) {
        // Key已存在
        PsiField psiField = msgClass.findFieldByName(key, false);
        if (psiField != null) {

            PsiDocComment psiDocComment = PsiTreeUtil.findChildOfType(psiField, PsiDocComment.class);
            if (psiDocComment != null) {
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    psiDocComment.replace(PsiUtils.createPsiDocComment(elementFactory, value));
                });
                PsiUtils.reformatJavaFile(msgClass);
            }

            return Status.EXISTS;
        }

        PsiEnumConstant psiEnumConstant = elementFactory.createEnumConstantFromText(key, null);

        PsiElement whiteSpace = PsiParserFacade.SERVICE.getInstance(project)
                .createWhiteSpaceFromText("\n\n");


        WriteCommandAction.runWriteCommandAction(project, () -> {
            psiEnumConstant.addBefore(PsiUtils.createPsiDocComment(elementFactory, value),
                    psiEnumConstant.getFirstChild());
            psiEnumConstant.addBefore(whiteSpace, psiEnumConstant.getFirstChild());
            msgClass.add(psiEnumConstant);
        });

        PsiUtils.reformatJavaFile(msgClass);

        return Status.SUCCESS;
    }

    /**
     * 把Key和Value放到粘贴板
     */
    @Func
    private void paste(String key, String value) {
        CopyPasteUtils.putString(value);
        CopyPasteUtils.putString(key);
    }

    private enum Status {
        SUCCESS, FAIL, EXISTS
    }
}
