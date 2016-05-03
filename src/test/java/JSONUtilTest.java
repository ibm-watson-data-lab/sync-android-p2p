/**
 * Copyright (c) 2013 Cloudant, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

import com.cloudant.sync.util.JSONUtils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This isn't really testing the JSONUtil, but for understanding how it works
 */
public class JSONUtilTest {

    @Test
    public void call_noFailure() throws Exception {

        Map<String, Object> requestJson =
                JSONUtils.deserialize("{\"abc\": [\"def\", \"ghi\"]}".getBytes());

        Map<String, Object> responseJson = new HashMap<String, Object>();

        Iterator requestIdIterator = requestJson.entrySet().iterator();
        while (requestIdIterator.hasNext()) {
            Map.Entry pair = (Map.Entry)requestIdIterator.next();

            String id = (String) pair.getKey();
            List<String> missingRevs = new ArrayList<String>();

            List<String> revs = (List<String>)pair.getValue();
            for (String rev : revs) {
                // TODO Check _id and _rev exists
                if (rev.equals("def")) {
                    missingRevs.add(rev);
                }
            }
            if (missingRevs.size() > 0) {
                Map<String, Object> missingMap = new HashMap<String, Object>();
                missingMap.put("missing", missingRevs);
                responseJson.put(id, missingMap);
            }
        }
        System.out.println(JSONUtils.serializeAsString(responseJson));
    }
}
