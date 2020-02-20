package com.parse;

import com.parse.codec.binary.Base64;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class ParseDecoder {
    ParseDecoder() {
    }

    /* access modifiers changed from: 0000 */
    public List<Object> convertJSONArrayToList(JSONArray array) {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            list.add(decode(array.opt(i)));
        }
        return list;
    }

    /* access modifiers changed from: 0000 */
    public Map<String, Object> convertJSONObjectToMap(JSONObject object) {
        Map<String, Object> outputMap = new HashMap<>();
        Iterator<String> it = object.keys();
        while (it.hasNext()) {
            String key = (String) it.next();
            outputMap.put(key, decode(object.opt(key)));
        }
        return outputMap;
    }

    /* access modifiers changed from: protected */
    public ParseObject decodePointer(String className, String objectId) {
        return ParseObject.createWithoutData(className, objectId);
    }

    public Object decode(Object object) {
        if (object instanceof JSONArray) {
            return convertJSONArrayToList((JSONArray) object);
        }
        if (!(object instanceof JSONObject)) {
            return object;
        }
        JSONObject jsonObject = (JSONObject) object;
        if (jsonObject.optString("__op", null) != null) {
            try {
                return ParseFieldOperations.decode(jsonObject, this);
            } catch (JSONException e) {
                RuntimeException runtimeException = new RuntimeException(e);
                throw runtimeException;
            }
        } else {
            String typeString = jsonObject.optString("__type", null);
            if (typeString == null) {
                return convertJSONObjectToMap(jsonObject);
            }
            if (typeString.equals("Date")) {
                return Parse.stringToDate(jsonObject.optString("iso"));
            }
            if (typeString.equals("Bytes")) {
                return Base64.decodeBase64(jsonObject.optString("base64"));
            }
            if (typeString.equals("Pointer")) {
                return decodePointer(jsonObject.optString("className"), jsonObject.optString("objectId"));
            } else if (typeString.equals("File")) {
                ParseFile parseFile = new ParseFile(jsonObject.optString("name"), jsonObject.optString("url"));
                return parseFile;
            } else if (typeString.equals("GeoPoint")) {
                try {
                    ParseGeoPoint parseGeoPoint = new ParseGeoPoint(jsonObject.getDouble("latitude"), jsonObject.getDouble("longitude"));
                    return parseGeoPoint;
                } catch (JSONException e2) {
                    RuntimeException runtimeException2 = new RuntimeException(e2);
                    throw runtimeException2;
                }
            } else if (typeString.equals("Object")) {
                JSONObject nested = new JSONObject();
                try {
                    nested.put("data", jsonObject);
                    ParseObject output = ParseObject.createWithoutData(jsonObject.optString("className", null), jsonObject.optString("objectId", null));
                    output.mergeAfterFetch(nested, this, true);
                    return output;
                } catch (JSONException e3) {
                    RuntimeException runtimeException3 = new RuntimeException(e3);
                    throw runtimeException3;
                }
            } else if (typeString.equals("Relation")) {
                ParseRelation parseRelation = new ParseRelation(jsonObject, this);
                return parseRelation;
            } else if (!typeString.equals("OfflineObject")) {
                return null;
            } else {
                throw new RuntimeException("An unexpected offline pointer was encountered.");
            }
        }
    }
}
