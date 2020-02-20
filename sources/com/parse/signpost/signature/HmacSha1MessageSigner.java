package com.parse.signpost.signature;

import com.parse.signpost.OAuth;
import com.parse.signpost.exception.OAuthMessageSignerException;
import com.parse.signpost.http.HttpParameters;
import com.parse.signpost.http.HttpRequest;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class HmacSha1MessageSigner extends OAuthMessageSigner {
    private static final String MAC_NAME = "HmacSHA1";

    public String getSignatureMethod() {
        return "HMAC-SHA1";
    }

    public String sign(HttpRequest request, HttpParameters requestParams) throws OAuthMessageSignerException {
        try {
            SecretKey key = new SecretKeySpec((OAuth.percentEncode(getConsumerSecret()) + '&' + OAuth.percentEncode(getTokenSecret())).getBytes("UTF-8"), MAC_NAME);
            Mac mac = Mac.getInstance(MAC_NAME);
            mac.init(key);
            String sbs = new SignatureBaseString(request, requestParams).generate();
            OAuth.debugOut("SBS", sbs);
            return base64Encode(mac.doFinal(sbs.getBytes("UTF-8"))).trim();
        } catch (GeneralSecurityException e) {
            throw new OAuthMessageSignerException((Exception) e);
        } catch (UnsupportedEncodingException e2) {
            throw new OAuthMessageSignerException((Exception) e2);
        }
    }
}
