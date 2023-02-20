package com.getpliant.ecs.vertical.autoscale.helper;

import org.json.simple.JSONObject;

/**
 *
 * @author Owner
 */
public class CommonHelper {

    private static JSONObject obj = new JSONObject();

    public static String generateResponse(String code, String Message) {
        obj.put("responseCode", code);
        obj.put("description", Message);
        return obj.toJSONString();
    }

    public static boolean isNotNull(String value) {
        boolean response = false;
        if (value != null && !value.trim().isEmpty() ) {
            response = true;
        }

        return response;
    }

}
