package com.parse;

import com.parse.ParseAuthenticationProvider.ParseAuthenticationCallback;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;

class AnonymousAuthenticationProvider implements ParseAuthenticationProvider {
    AnonymousAuthenticationProvider() {
    }

    public void authenticate(ParseAuthenticationCallback callback) {
        try {
            callback.onSuccess(getAuthData());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public JSONObject getAuthData() throws JSONException {
        JSONObject authData = new JSONObject();
        authData.put("id", UUID.randomUUID());
        return authData;
    }

    public void deauthenticate() {
    }

    public boolean restoreAuthentication(JSONObject authData) {
        return true;
    }

    public void cancel() {
    }

    public String getAuthType() {
        return "anonymous";
    }
}
