package com.ganguo.plugin.util;

public class MyStringUtils {

    /**
     * 将首字母变为小写
     *
     * @param str 字符串
     * @return 转换后的字符串
     */
    public static String lowerCaseFirstChar(String str) {
        if (str == null || str.isEmpty()) return str;

        char firstChar = str.charAt(0);
        return Character.isLowerCase(firstChar) ? str :
                Character.toLowerCase(firstChar) + str.substring(1);
    }

    /**
     * 驼峰转下划线
     *
     * @param str 字符串
     * @return 转换后的字符串
     */
    public static String camelCase2UnderScoreCase(String str) {
        if (str == null || str.isEmpty()) return str;
        StringBuilder sb = new StringBuilder(str.length());

        boolean prevIsUpperCase = true;
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            boolean isUpperCase = Character.isUpperCase(ch);

            if (!prevIsUpperCase && isUpperCase) {
                sb.append('_');
            }
            sb.append(isUpperCase ? Character.toLowerCase(ch) : ch);

            prevIsUpperCase = isUpperCase;
        }

        return sb.toString();
    }

    /**
     * 下划线转驼峰
     *
     * @param str 字符串
     * @return 转换后的字符串
     */
    public static String underScoreCase2CamelCase(String str) {
        if (str == null || str.isEmpty()) return str;
        StringBuilder sb = new StringBuilder(str.length());

        boolean prevIsUnderline = false;
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);

            if (ch == '_') {
                prevIsUnderline = true;
            } else {
                if (prevIsUnderline) {
                    sb.append(Character.toUpperCase(ch));
                    prevIsUnderline = false;
                } else {
                    sb.append(ch);
                }
            }
        }

        return sb.toString();
    }
}