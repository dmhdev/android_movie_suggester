package com.parse;

import android.net.http.AndroidHttpClient;
import com.parse.entity.mime.HttpMultipartMode;
import com.parse.entity.mime.MIME;
import com.parse.entity.mime.content.ByteArrayBody;
import com.parse.entity.mime.content.StringBody;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.json.JSONException;
import org.json.JSONObject;

class ParseAWSRequest extends ParseRequest<byte[], byte[]> {
    private byte[] data;
    private String mimeType;
    private JSONObject postParams;
    private ProgressCallback progressCallback;

    public ParseAWSRequest(String url) {
        super(url);
    }

    public ParseAWSRequest(int method, String url) {
        super(method, url);
    }

    public void setMimeType(String mimeType2) {
        this.mimeType = mimeType2;
    }

    public void setPostParams(JSONObject postParams2) {
        this.postParams = postParams2;
    }

    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }

    public void setData(byte[] data2) {
        this.data = data2;
    }

    /* access modifiers changed from: protected */
    public HttpEntity newEntity() {
        CountingMultipartEntity entity = new CountingMultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE, this.progressCallback);
        try {
            entity.addPart(MIME.CONTENT_TYPE, new StringBody(this.mimeType));
            Iterator<String> keys = this.postParams.keys();
            while (keys.hasNext()) {
                String key = (String) keys.next();
                try {
                    entity.addPart(key, new StringBody(this.postParams.getString(key)));
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e.getMessage());
                } catch (JSONException e2) {
                    throw new RuntimeException(e2.getMessage());
                }
            }
            entity.addPart("file", new ByteArrayBody(this.data, this.mimeType, "file"));
            return entity;
        } catch (UnsupportedEncodingException e3) {
            throw new RuntimeException(e3.getMessage());
        }
    }

    /* access modifiers changed from: protected */
    public byte[] onResponse(HttpResponse response, ProgressCallback progressCallback2) throws IOException, ParseException {
        if (this.method == 0) {
            int totalSize = -1;
            Header[] contentLengthHeader = response.getHeaders("Content-Length");
            if (contentLengthHeader.length > 0) {
                totalSize = Integer.parseInt(contentLengthHeader[0].getValue());
            }
            int downloadedSize = 0;
            InputStream responseStream = AndroidHttpClient.getUngzippedContent(response.getEntity());
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data2 = new byte[32768];
            while (true) {
                int nRead = responseStream.read(data2, 0, data2.length);
                if (nRead == -1) {
                    return buffer.toByteArray();
                }
                buffer.write(data2, 0, nRead);
                downloadedSize += nRead;
                if (!(progressCallback2 == null || totalSize == -1)) {
                    Parse.callbackOnMainThreadAsync(Task.forResult(Integer.valueOf(Math.round((((float) downloadedSize) / ((float) totalSize)) * 100.0f))), progressCallback2);
                }
            }
        } else {
            int statusCode = response.getStatusLine().getStatusCode();
            if ((statusCode >= 200 && statusCode < 300) || statusCode == 304) {
                return null;
            }
            throw new ParseException(100, String.format("Upload to S3 failed. %s", new Object[]{response.getStatusLine().getReasonPhrase()}));
        }
    }
}
