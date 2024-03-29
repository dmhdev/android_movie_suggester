package com.parse.codec.language;

import com.parse.codec.EncoderException;
import com.parse.codec.StringEncoder;

public class Caverphone implements StringEncoder {
    private final Caverphone2 encoder = new Caverphone2();

    public String caverphone(String source) {
        return this.encoder.encode(source);
    }

    public Object encode(Object pObject) throws EncoderException {
        if (pObject instanceof String) {
            return caverphone((String) pObject);
        }
        throw new EncoderException("Parameter supplied to Caverphone encode is not of type java.lang.String");
    }

    public String encode(String pString) {
        return caverphone(pString);
    }

    public boolean isCaverphoneEqual(String str1, String str2) {
        return caverphone(str1).equals(caverphone(str2));
    }
}
