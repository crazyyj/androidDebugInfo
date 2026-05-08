package com.newchar.debug.base.utils;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.text.TextUtils;
import android.text.format.DateFormat;

public class DateUtils {

	/** 
     * 时间戳转换成日期格式字符串 
     * @param seconds 毫秒
     * @param format 可以传null 或 ""
     * @return 
     */  
    public static String timeStamp2Date(String seconds, String format) {
        if(seconds == null || seconds.isEmpty() || seconds.equals("null")) 
        	return "";  
        if(format == null || format.isEmpty()) 
        	format = "yyyy-MM-dd HH:mm:ss";  
        SimpleDateFormat sdf = new SimpleDateFormat(format);  
        return sdf.format(new Date(Long.parseLong(seconds)));
    }
    
    /**
     * 20150101 格式化 此格式的 日期
     * @param date
     * @return 返回 2015年01月01日
     */
	public static String formatStr(String date){
    	try {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
			Date formatDate = sdf.parse(date);
			String format = new SimpleDateFormat("yyyy年MM月dd日").format(formatDate);
			return format;
		} catch (ParseException e) {
			e.printStackTrace();
			return "1970年01月01日";
		}
    }


    public static String getStringToday() {
        Date currentTime = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String dateString = formatter.format(currentTime);
        return dateString;
    }

    /** 
     * 日期格式字符串转换成时间戳 
     * @param date_str 字符串日期
     * @param format 如：yyyy-MM-dd HH:mm:ss 
     * @return 
     */  
    public static String date2TimeStamp(String date_str,String format){ 
        try {  
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            return String.valueOf(sdf.parse(date_str).getTime() / 1000);
        } catch (Exception e) {  
            e.printStackTrace();  
        }  
        return "";
    }
    
    /**
     * 将月份9 转换成09
     * @param i == 9
     * @return 09
     */
    public static String min10intformat(int i) {
		String str = Integer.toString(i);
		return (i < 10 && str.length() < 2) ? "0".concat(str) : str;
	}
    
    /**
     *	获取当前年月日数组。 
     * @return
     *		第一位是 年
     *		第二位是月
     *		第三位是日
     */
    public static int[] getDay(){
    	int[] i = new int[3];
    	Calendar mCalendar = Calendar.getInstance(Locale.getDefault());
		i[0] = mCalendar.get(Calendar.YEAR);
		i[1] = mCalendar.get(Calendar.MONTH);
		i[2] = mCalendar.get(Calendar.DAY_OF_MONTH);
		return i;
    }
    
    /**
     * 获取当前时间 时分秒 数组
     * @return 0位 是小时， 1 位 是当前分钟 2 位是当前秒
     */
	public static int[] getTime() {
		int[] timearr = new int[3];
		Calendar calendar = Calendar.getInstance(Locale.getDefault());
		timearr[0] = calendar.get(Calendar.HOUR_OF_DAY);
		timearr[1] = calendar.get(Calendar.MINUTE);
		timearr[2] = calendar.get(Calendar.SECOND);
		return timearr;
	}
    
    /**
     * 当前时间
     * @return 固定格式的字符串
     */
    public static String date2String(){
    	SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
    	return sdf.format(new Date());
    }
    
}
