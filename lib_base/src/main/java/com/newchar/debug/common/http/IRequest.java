package com.newchar.debug.common.http;

import java.util.Map;

/**
 * Created by newlq on 2017/2/20.
 */

public interface IRequest {

    public static final int METHOD_GET = 0;
    public static final int METHOD_POST = 1;
    public static final int METHOD_DELETE = 5;
    public static final int METHOD_PUT = 6;

    public static final int METHOD_DEFAULT = METHOD_GET;

    void post(String url, Map<String, String> pmaram);

    void get(String url);

    void otherRequest(int otherMethod, String url, Map<String, String> pmaram);


}
