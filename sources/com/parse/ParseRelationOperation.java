package com.parse;

import com.parse.ParseObject;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class ParseRelationOperation<T extends ParseObject> implements ParseFieldOperation {
    private Set<ParseObject> relationsToAdd;
    private Set<ParseObject> relationsToRemove;
    private String targetClass;

    ParseRelationOperation(Set<T> newRelationsToAdd, Set<T> newRelationsToRemove) {
        this.targetClass = null;
        this.relationsToAdd = new HashSet();
        this.relationsToRemove = new HashSet();
        if (newRelationsToAdd != null) {
            for (T object : newRelationsToAdd) {
                addParseObjectToSet(object, this.relationsToAdd);
                if (this.targetClass == null) {
                    this.targetClass = object.getClassName();
                } else if (!this.targetClass.equals(object.getClassName())) {
                    throw new IllegalArgumentException("All objects in a relation must be of the same class.");
                }
            }
        }
        if (newRelationsToRemove != null) {
            for (T object2 : newRelationsToRemove) {
                addParseObjectToSet(object2, this.relationsToRemove);
                if (this.targetClass == null) {
                    this.targetClass = object2.getClassName();
                } else if (!this.targetClass.equals(object2.getClassName())) {
                    throw new IllegalArgumentException("All objects in a relation must be of the same class.");
                }
            }
        }
        if (this.targetClass == null) {
            throw new IllegalArgumentException("Cannot create a ParseRelationOperation with no objects.");
        }
    }

    private ParseRelationOperation(String newTargetClass, Set<ParseObject> newRelationsToAdd, Set<ParseObject> newRelationsToRemove) {
        this.targetClass = newTargetClass;
        this.relationsToAdd = new HashSet(newRelationsToAdd);
        this.relationsToRemove = new HashSet(newRelationsToRemove);
    }

    private void addParseObjectToSet(ParseObject obj, Set<ParseObject> set) {
        if (obj.getObjectId() == null) {
            set.add(obj);
            return;
        }
        for (ParseObject existingObject : set) {
            if (obj.getObjectId().equals(existingObject.getObjectId())) {
                set.remove(existingObject);
            }
        }
        set.add(obj);
    }

    private void addAllParseObjectsToSet(Collection<ParseObject> list, Set<ParseObject> set) {
        for (ParseObject obj : list) {
            addParseObjectToSet(obj, set);
        }
    }

    private void removeParseObjectFromSet(ParseObject obj, Set<ParseObject> set) {
        if (obj.getObjectId() == null) {
            set.remove(obj);
            return;
        }
        for (ParseObject existingObject : set) {
            if (obj.getObjectId().equals(existingObject.getObjectId())) {
                set.remove(existingObject);
            }
        }
    }

    private void removeAllParseObjectsFromSet(Collection<ParseObject> list, Set<ParseObject> set) {
        for (ParseObject obj : list) {
            removeParseObjectFromSet(obj, set);
        }
    }

    /* access modifiers changed from: 0000 */
    public String getTargetClass() {
        return this.targetClass;
    }

    /* access modifiers changed from: 0000 */
    public JSONArray convertSetToArray(Set<ParseObject> set, ParseObjectEncodingStrategy objectEncoder) throws JSONException {
        JSONArray array = new JSONArray();
        for (ParseObject obj : set) {
            array.put(Parse.encode(obj, objectEncoder));
        }
        return array;
    }

    public JSONObject encode(ParseObjectEncodingStrategy objectEncoder) throws JSONException {
        JSONObject adds = null;
        JSONObject removes = null;
        if (this.relationsToAdd.size() > 0) {
            adds = new JSONObject();
            adds.put("__op", "AddRelation");
            adds.put("objects", convertSetToArray(this.relationsToAdd, objectEncoder));
        }
        if (this.relationsToRemove.size() > 0) {
            removes = new JSONObject();
            removes.put("__op", "RemoveRelation");
            removes.put("objects", convertSetToArray(this.relationsToRemove, objectEncoder));
        }
        if (adds != null && removes != null) {
            JSONObject result = new JSONObject();
            result.put("__op", "Batch");
            JSONArray ops = new JSONArray();
            ops.put(adds);
            ops.put(removes);
            result.put("ops", ops);
            return result;
        } else if (adds != null) {
            return adds;
        } else {
            if (removes != null) {
                return removes;
            }
            throw new IllegalArgumentException("A ParseRelationOperation was created without any data.");
        }
    }

    public ParseFieldOperation mergeWithPrevious(ParseFieldOperation previous) {
        if (previous == null) {
            return this;
        }
        if (previous instanceof ParseDeleteOperation) {
            throw new IllegalArgumentException("You can't modify a relation after deleting it.");
        } else if (previous instanceof ParseRelationOperation) {
            ParseRelationOperation<T> previousOperation = (ParseRelationOperation) previous;
            if (previousOperation.targetClass == null || previousOperation.targetClass.equals(this.targetClass)) {
                Set<ParseObject> newRelationsToAdd = new HashSet<>(previousOperation.relationsToAdd);
                Set<ParseObject> newRelationsToRemove = new HashSet<>(previousOperation.relationsToRemove);
                if (this.relationsToAdd != null) {
                    addAllParseObjectsToSet(this.relationsToAdd, newRelationsToAdd);
                    removeAllParseObjectsFromSet(this.relationsToAdd, newRelationsToRemove);
                }
                if (this.relationsToRemove != null) {
                    removeAllParseObjectsFromSet(this.relationsToRemove, newRelationsToAdd);
                    addAllParseObjectsToSet(this.relationsToRemove, newRelationsToRemove);
                }
                return new ParseRelationOperation(this.targetClass, newRelationsToAdd, newRelationsToRemove);
            }
            throw new IllegalArgumentException("Related object object must be of class " + previousOperation.targetClass + ", but " + this.targetClass + " was passed in.");
        } else {
            throw new IllegalArgumentException("Operation is invalid after previous operation.");
        }
    }

    public Object apply(Object oldValue, ParseObject object, String key) {
        if (oldValue == null) {
            ParseRelation<T> relation = new ParseRelation<>(object, key);
            relation.setTargetClass(this.targetClass);
            return relation;
        } else if (oldValue instanceof ParseRelation) {
            ParseRelation<T> relation2 = (ParseRelation) oldValue;
            if (this.targetClass == null || this.targetClass.equals(relation2.getTargetClass())) {
                return relation2;
            }
            throw new IllegalArgumentException("Related object object must be of class " + relation2.getTargetClass() + ", but " + this.targetClass + " was passed in.");
        } else {
            throw new IllegalArgumentException("Operation is invalid after previous operation.");
        }
    }
}
