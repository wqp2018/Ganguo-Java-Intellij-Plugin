package com.ganguo.java.plugin.action.menu;

import com.ganguo.java.plugin.action.BaseAnAction;
import com.ganguo.java.plugin.util.ActionShowHelper;
import com.ganguo.java.plugin.util.MyStringUtils;
import com.ganguo.java.plugin.util.WriteActions;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 格式化SQL脚本
 */
public class FormatSqlAction extends BaseAnAction {

    private static final Pattern PATTERN_FORMAT_INSERT = Pattern.compile(
            "^([^(]*?)\\(([\\s\\S]*)\\)\\s*(VALUES)\\s*([\\s\\S]*)$",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    @Override
    protected void action(AnActionEvent e) throws Exception {
        Editor editor = e.getData(LangDataKeys.EDITOR);
        if (editor == null) {
            return;
        }

        Document doc = editor.getDocument();
        SelectionModel selectionModel = editor.getSelectionModel();
        boolean hasSelection = selectionModel.hasSelection();
        int selectionStart;
        int selectionEnd;
        String text;
        if (hasSelection) {
            // 选中模式
            selectionStart = selectionModel.getSelectionStart();
            selectionEnd = selectionModel.getSelectionEnd();
            text = selectionModel.getSelectedText();
        } else {
            // 全文模式
            selectionStart = 0;
            selectionEnd = 0;
            text = doc.getText();
        }

        String resultText = applyInsert(text);

        // 写入文件
        new WriteActions(e.getProject()).add(() -> {
            if (hasSelection) {
                doc.replaceString(selectionStart, selectionEnd, resultText);
            } else if (!text.equals(resultText)) {
                doc.setText(resultText);
            }
        }).run();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        ActionShowHelper.of(e)
                .fileNameMatch(".*\\.sql")
                .and(() -> e.getData(LangDataKeys.EDITOR) != null)
                .update();
    }

    /**
     * 处理插入语句
     *
     * @param text 源文本
     * @return 处理结果文本
     */
    private String applyInsert(String text) {
        StringBuilder resultText = new StringBuilder();
        String endStr = ");";
        int index = 0;
        int from = 0;
        do {
            index = MyStringUtils.indexOf(text, "INSERT", index, "\"'", true);
            if (index != -1) {

                int start = index;
                int end = MyStringUtils.indexOf(text, endStr, index, "\"'", false);
                if (end != -1) {
                    resultText.append(text, from, index);

                    end += endStr.length();
                    index = end;
                    from = end;
                    String sql = text.substring(start, end);
                    resultText.append(formatInsert(sql));
                } else {
                    index = -1;
                }
            }
        } while (index >= 0 && index < text.length());

        if (from < text.length()) {
            resultText.append(text, from, text.length());
        }
        return resultText.toString();
    }

    /**
     * 格式化INSERT语句
     */
    public String formatInsert(String sql) {
        Matcher matcher = PATTERN_FORMAT_INSERT.matcher(sql);
        if (!matcher.find()) {
            return sql;
        }

        String insertStatement = matcher.group(1).trim();
        String columnsStr = matcher.group(2).trim();
        String valueKeyword = matcher.group(3).trim();
        String valuesStr = matcher.group(4).trim();

        String[] columns = getColumns(columnsStr);
        List<String[]> values = getValues(valuesStr);
        int[] widths = getWidths(columns, values);

        fillColumnsBlank(columns, widths);
        fillValuesBlank(values, widths);

        columnsStr = MyStringUtils.wrapWithBrackets(StringUtils.join(columns, ", "));

        valuesStr = StringUtils.join(values.stream()
                .map(item -> MyStringUtils.wrapWithBrackets(StringUtils.join(item, ", ")))
                .toArray(String[]::new), ",\n");
        if (StringUtils.isNotEmpty(valuesStr)) {
            valuesStr += ";";
        }

        return insertStatement + "\n" + columnsStr + "\n" + valueKeyword + "\n" + valuesStr;
    }

    /**
     * 获取字段
     */
    private String[] getColumns(String columnsStr) {
        return Arrays.stream(StringUtils.split(columnsStr, ","))
                .map(column -> column.trim().replace("`", ""))
                .map(column -> MyStringUtils.wrap(column, "`"))
                .toArray(String[]::new);
    }

    /**
     * 获取值列表
     */
    private List<String[]> getValues(String valuesStr) {
        return Arrays.stream(MyStringUtils.split(valuesStr, "),", "'\""))
                .map(String::trim)
                .map(it -> Arrays.stream(MyStringUtils.split(it, ",", "'\""))
                        .map(String::trim)
                        .map(str -> str.startsWith("(") ? str.substring(1) : str)
                        .map(str -> str.endsWith(");") ? str.substring(0, str.length() - 2) : str)
                        .toArray(String[]::new))
                .collect(Collectors.toList());
    }

    /**
     * 计算各列的最大宽度
     */
    private int[] getWidths(String[] columns, List<String[]> values) {
        int[] lengths = new int[columns.length];
        for (int i = 0; i < lengths.length; i++) {
            int finalI = i;
            lengths[i] = Math.max(MyStringUtils.width(columns[i]), values.stream()
                    .map(it -> finalI < it.length ? MyStringUtils.width(it[finalI]) : 0)
                    .max(Comparator.comparingInt(Integer::intValue))
                    .orElse(0));
        }
        return lengths;
    }

    /**
     * 填充字段空白
     */
    private void fillColumnsBlank(String[] columns, int[] lengths) {
        for (int i = 0; i < columns.length; i++) {
            String column = columns[i];
            int len = lengths[i];
            int n = len - MyStringUtils.width(column);
            if (n > 0) {
                columns[i] = column + StringUtils.repeat(' ', n);
            }
        }
    }

    /**
     * 填充值空白
     */
    private void fillValuesBlank(List<String[]> values, int[] lengths) {
        values.forEach(value -> {
            for (int i = 0; i < value.length; i++) {
                String item = value[i];
                int len = lengths[i];
                int n = len - MyStringUtils.width(item);
                if (n > 0) {
                    if (item.contains("'") || item.contains("\"")) {
                        value[i] = item + StringUtils.repeat(' ', n);
                    } else {
                        value[i] = StringUtils.repeat(' ', n) + item;
                    }
                }
            }
        });
    }
}
