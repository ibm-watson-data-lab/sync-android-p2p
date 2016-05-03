package com.cloudant.sync.util;

import java.util.List;

/**
 * Created by snowch on 30/01/15.
 */
public class ExtendedJSONUtils extends JSONUtils {

    public static String serializeAsString(List object) {
        return serializeAsString(object, true);
    }
}
