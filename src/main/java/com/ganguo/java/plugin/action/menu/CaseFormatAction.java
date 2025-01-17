package com.ganguo.java.plugin.action.menu;

import com.ganguo.java.plugin.action.BaseReplaceAction;
import com.ganguo.java.plugin.util.MyStringUtils;
import com.intellij.openapi.actionSystem.AnActionEvent;

/**
 * 驼峰-下划线互转
 */
public class CaseFormatAction extends BaseReplaceAction {

    @Override
    protected String replace(AnActionEvent e, String text) throws Exception {
        return text.contains("_") ? MyStringUtils.underScoreCase2CamelCase(text) :
                MyStringUtils.camelCase2UnderScoreCase(text);
    }
}
