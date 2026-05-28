package com.newchar.debug.utils;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author NewLq
 *         Created by NewLq on 2016/1/8.
 */
public class TextUtils {


    /**
     * 字符串高亮
     *
     * @param color   变化的颜色
     * @param fullStr 完整字符串
     * @param keyWord 要高亮变色的字符
     * @return
     */
    public static SpannableString strHighLight(int color, String fullStr, String keyWord) {
        SpannableString str = new SpannableString(fullStr);
        Pattern p = Pattern.compile(keyWord);
        Matcher matcher = p.matcher(str);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            str.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return str;
    }

    public static final boolean isEmpty(String text){
        return "null".equalsIgnoreCase(text) || android.text.TextUtils.isEmpty(text);
    }

    /**
     * 多个关键字高亮
     *
     * @param color   变化的颜色
     * @param fullStr 完整字符串
     * @param keyWord 要高亮变色的多个字符串
     * @return
     */
    public static SpannableString moreStrHighLight(int color, String fullStr, String... keyWord) {
        SpannableString s = new SpannableString(fullStr);
        for (int i = 0; i < keyWord.length; i++) {
            Pattern p = Pattern.compile(keyWord[i]);
            Matcher m = p.matcher(s);
            while (m.find()) {
                int start = m.start();
                int end = m.end();
                s.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return s;
    }

    public static boolean isChinese(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        if (ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || ub == Character.UnicodeBlock.GENERAL_PUNCTUATION
                || ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || ub == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS) {
            return true;
        }
        return false;
    }

    /**
     * 验证身份证是否合法 (最后一位)
     *
     */
    static final int[] weight = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};    //十七位数字本体码权重
    static final char[] validate = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};    //mod11,对应校验码字符值

    public static char getValidateCode(String id17) {
        int sum = 0;
        int mode = 0;
        for (int i = 0; i < id17.length(); i++) {
            sum = sum + Integer.parseInt(String.valueOf(id17.charAt(i))) * weight[i];
        }
        mode = sum % 11;
        return validate[mode];
    }

}