package com.parse;

import java.util.Map;
import org.json.JSONObject;

public class ParseCloud {
    private static ParseCommand constructCallCommand(String name, Map<String, ?> params) {
        ParseCommand command = new ParseCommand("client_function", ParseUser.getCurrentSessionToken());
        command.put("data", (JSONObject) Parse.encode(params, NoObjectsEncodingStrategy.get()));
        command.put("function", name);
        return command;
    }

    /* access modifiers changed from: private */
    public static Object convertCloudResponse(Object result) {
        Object finalResult = new ParseDecoder().decode(result);
        return finalResult == null ? result : finalResult;
    }

    private static <T> Task<T> callFunctionAsync(String name, Map<String, ?> params) {
        return constructCallCommand(name, params).executeAsync().onSuccess(new Continuation<Object, T>() {
            public T then(Task<Object> task) throws Exception {
                return ParseCloud.convertCloudResponse(task.getResult());
            }
        });
    }

    public static <T> T callFunction(String name, Map<String, ?> params) throws ParseException {
        return Parse.waitForTask(callFunctionAsync(name, params));
    }

    public static <T> void callFunctionInBackground(String name, Map<String, ?> params, FunctionCallback<T> callback) {
        Parse.callbackOnMainThreadAsync(callFunctionAsync(name, params), callback);
    }
}
