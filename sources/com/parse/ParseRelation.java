package com.parse;

import com.parse.ParseObject;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ParseRelation<T extends ParseObject> {
    private String key;
    private Set<ParseObject> knownObjects;
    private Object mutex;
    private ParseObject parent;
    private String targetClass;

    ParseRelation(ParseObject parent2, String key2) {
        this.mutex = new Object();
        this.knownObjects = new HashSet();
        this.parent = parent2;
        this.key = key2;
        this.targetClass = null;
    }

    ParseRelation(String targetClass2) {
        this.mutex = new Object();
        this.knownObjects = new HashSet();
        this.parent = null;
        this.key = null;
        this.targetClass = targetClass2;
    }

    ParseRelation(JSONObject jsonObject, ParseDecoder decoder) {
        this.mutex = new Object();
        this.knownObjects = new HashSet();
        this.parent = null;
        this.targetClass = jsonObject.optString("className", null);
        this.key = null;
        JSONArray objectsArray = jsonObject.optJSONArray("objects");
        if (objectsArray != null) {
            for (int i = 0; i < objectsArray.length(); i++) {
                this.knownObjects.add((ParseObject) decoder.decode(objectsArray.optJSONObject(i)));
            }
        }
    }

    /* access modifiers changed from: 0000 */
    public void ensureParentAndKey(ParseObject someParent, String someKey) {
        synchronized (this.mutex) {
            if (this.parent == null) {
                this.parent = someParent;
            }
            if (this.key == null) {
                this.key = someKey;
            }
            if (this.parent != someParent) {
                throw new IllegalStateException("Internal error. One ParseRelation retrieved from two different ParseObjects.");
            } else if (!this.key.equals(someKey)) {
                throw new IllegalStateException("Internal error. One ParseRelation retrieved from two different keys.");
            }
        }
    }

    public void add(T object) {
        synchronized (this.mutex) {
            ParseRelationOperation<T> operation = new ParseRelationOperation<>(Collections.singleton(object), null);
            this.targetClass = operation.getTargetClass();
            this.parent.performOperation(this.key, operation);
            this.knownObjects.add(object);
        }
    }

    public void remove(T object) {
        synchronized (this.mutex) {
            ParseRelationOperation<T> operation = new ParseRelationOperation<>(null, Collections.singleton(object));
            this.targetClass = operation.getTargetClass();
            this.parent.performOperation(this.key, operation);
            this.knownObjects.remove(object);
        }
    }

    public ParseQuery<T> getQuery() {
        ParseQuery<T> query;
        synchronized (this.mutex) {
            if (this.targetClass == null) {
                query = ParseQuery.getQuery(this.parent.getClassName());
                query.redirectClassNameForKey(this.key);
            } else {
                query = ParseQuery.getQuery(this.targetClass);
            }
            query.whereRelatedTo(this.parent, this.key);
        }
        return query;
    }

    /* access modifiers changed from: 0000 */
    public JSONObject encodeToJSON(ParseObjectEncodingStrategy objectEncoder) throws JSONException {
        JSONObject relation;
        synchronized (this.mutex) {
            relation = new JSONObject();
            relation.put("__type", "Relation");
            relation.put("className", this.targetClass);
            JSONArray knownObjectsArray = new JSONArray();
            for (ParseObject knownObject : this.knownObjects) {
                try {
                    knownObjectsArray.put(objectEncoder.encodeRelatedObject(knownObject));
                } catch (Exception e) {
                }
            }
            relation.put("objects", knownObjectsArray);
        }
        return relation;
    }

    /* access modifiers changed from: 0000 */
    public String getTargetClass() {
        String str;
        synchronized (this.mutex) {
            str = this.targetClass;
        }
        return str;
    }

    /* access modifiers changed from: 0000 */
    public void setTargetClass(String className) {
        synchronized (this.mutex) {
            this.targetClass = className;
        }
    }
}
