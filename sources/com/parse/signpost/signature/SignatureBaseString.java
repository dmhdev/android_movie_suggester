package com.parse.signpost.signature;

import com.parse.signpost.OAuth;
import com.parse.signpost.exception.OAuthMessageSignerException;
import com.parse.signpost.http.HttpParameters;
import com.parse.signpost.http.HttpRequest;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class SignatureBaseString {
    private HttpRequest request;
    private HttpParameters requestParameters;

    public SignatureBaseString(HttpRequest request2, HttpParameters requestParameters2) {
        this.request = request2;
        this.requestParameters = requestParameters2;
    }

    public String generate() throws OAuthMessageSignerException {
        try {
            return this.request.getMethod() + '&' + OAuth.percentEncode(normalizeRequestUrl()) + '&' + OAuth.percentEncode(normalizeRequestParameters());
        } catch (Exception e) {
            throw new OAuthMessageSignerException(e);
        }
    }

    public String normalizeRequestUrl() throws URISyntaxException {
        boolean dropPort;
        URI uri = new URI(this.request.getRequestUrl());
        String scheme = uri.getScheme().toLowerCase();
        String authority = uri.getAuthority().toLowerCase();
        if ((!scheme.equals("http") || uri.getPort() != 80) && (!scheme.equals("https") || uri.getPort() != 443)) {
            dropPort = false;
        } else {
            dropPort = true;
        }
        if (dropPort) {
            int index = authority.lastIndexOf(":");
            if (index >= 0) {
                authority = authority.substring(0, index);
            }
        }
        String path = uri.getRawPath();
        if (path == null || path.length() <= 0) {
            path = "/";
        }
        return scheme + "://" + authority + path;
    }

    public String normalizeRequestParameters() throws IOException {
        if (this.requestParameters == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (String param : this.requestParameters.keySet()) {
            if (!OAuth.OAUTH_SIGNATURE.equals(param) && !"realm".equals(param)) {
                if (i > 0) {
                    sb.append("&");
                }
                sb.append(this.requestParameters.getAsQueryString(param));
            }
            i++;
        }
        return sb.toString();
    }
}
