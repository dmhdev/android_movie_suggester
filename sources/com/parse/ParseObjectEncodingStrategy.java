package com.parse;

import org.json.JSONObject;

interface ParseObjectEncodingStrategy {
    JSONObject encodeRelatedObject(ParseObject parseObject);
}
