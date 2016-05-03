package com.cloudant.p2p.listener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import com.cloudant.sync.datastore.BasicDocumentRevision;
import com.cloudant.sync.datastore.Datastore;
import com.cloudant.sync.datastore.DatastoreExtended;
import com.cloudant.sync.datastore.DatastoreManager;
import com.cloudant.sync.datastore.DatastoreNotCreatedException;
import com.cloudant.sync.datastore.DocumentBody;
import com.cloudant.sync.datastore.DocumentBodyFactory;
import com.cloudant.sync.datastore.DocumentException;
import com.cloudant.sync.datastore.DocumentRevisionBuilder;
import com.cloudant.sync.datastore.MutableDocumentRevision;
import com.cloudant.sync.util.ExtendedJSONUtils;
import com.cloudant.sync.util.JSONUtils;

// Do not use this code for production - it is only a proof-of-concept.
public class HttpListener extends ServerResource {
	
	public int port;
	
	public String databaseDir;

    private static final Logger logger = Logger.getLogger(HttpListener.class.getCanonicalName());
    
    // TODO need to ensure static access is thread-safe
    private static DatastoreManager manager;

    public HttpListener() {

        databaseDir = 
        		getApplication().getContext().getParameters().getFirstValue("databaseDir");

        port = new Integer(
        			getApplication().getContext().getParameters().getFirstValue("port")
        			);
        
        manager = new DatastoreManager(databaseDir);
    }

    @java.lang.Override
    protected void doInit() throws ResourceException {
        super.doInit();
    }

    @Get() // @Get is required for both GET and HEAD requests
    public Representation handleHeadAndGet() {

        String path = getReference().getPath();
        String dbname = getDatabaseName(path);
        
        if (!dbNameExists(dbname)) {
            return databaseNotFound();
        }
        else if (path.contains("/_local/")) {
            return handleLocalGet(path, dbname);
        }
        else if (path.contains("/_changes/")) {
            return handleChangesGet(path);
        } else {
            if (Method.HEAD.equals(getMethod())) {
                return httpSuccess();
            } else {
                return lastSequence(dbname);
            }
        }
    }

    @Post("json")
    public Representation handlePost(Representation entity) {

        String path = getReference().getPath();

        String dbname = getDatabaseName(path);
        
        if (!dbNameExists(dbname)) {
            return databaseNotFound();
        }
        else if (path.endsWith("/_revs_diff")) {
            return handleRevsDiff(dbname);
        }
        else if (path.endsWith("/_ensure_full_commit")) {
            return handleEnsureFullCommitPost(dbname);
        }
        else if (path.endsWith("/_bulk_docs")) {
            return handleBulkDocsPost(dbname);
        }
        else {
        	getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
        	return new StringRepresentation("{\"message\": \"Server error for request: " + getMethod() + " " + path + "\"}", MediaType.TEXT_PLAIN);
        }
    }

    @Put("json")
    public Representation handlePut(Representation entity) {

        String path = getReference().getPath();

        String dbname = getDatabaseName(path);

        String pathWithFirstAndLastSlashRemoved = path.replaceAll("^/", "").replaceAll("/$", "");

        if (pathWithFirstAndLastSlashRemoved.equals(dbname)) {
            // must be a PUT /target request
            return handleCreateTargetPut(dbname);
        }
        else if (!dbNameExists(dbname)) {
            return databaseNotFound();
        }
        else if (path.contains("/_local/")) {
            return handleLocalPut(dbname);
        } else {
        	getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
        	return new StringRepresentation("{\"message\": \"Server error for request: " + getMethod() + " " + path + "\"}", MediaType.TEXT_PLAIN);
        }
    }

    private Representation handleCreateTargetPut(String dbname) {

        // TODO - authenticate request
        /*
            HTTP/1.1 500 Internal Server Error
            Cache-Control: must-revalidate
            Content-Length: 108
            Content-Type: application/json
            Date: Fri, 09 May 2014 13:50:32 GMT
            Server: CouchDB (Erlang OTP)

            {
              "error": "unauthorized",
              "reason": "unauthorized to access or create database http://localhost:5984/target"
            }
        */

        // TODO this method does not check and handle failure to create the database
        Datastore ds;
		try {
			ds = manager.openDatastore(dbname);
			ds.close();
		} catch (DatastoreNotCreatedException e) {
			throw new RuntimeException(e);
		}

        String body = "{ \"ok\": true }";
        getResponse().setStatus(Status.SUCCESS_CREATED);
        return new StringRepresentation(body, MediaType.APPLICATION_JSON);
    }

    private Representation handleLocalGet(String path, String dbname) {

        /*
        Strip the database name from the path. E.g
        
        If the path is:      /target/_local/afa899a9e59589c3d4ce5668e3218aef 
        then the id will be: _local/afa899a9e59589c3d4ce5668e3218aef
        */
        
        String id = path.replaceAll("/"+dbname+"/", "");

        Datastore ds;
		try {
			ds = manager.openDatastore(dbname);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
        BasicDocumentRevision retrieved = null;
        
		try {
			retrieved = ds.getDocument(id);
		} catch (Exception e) {
			// do nothing
		} finally {
			ds.close();
		}

        if (retrieved != null) {
            System.out.println(port + " handleLocalGet: found id " + retrieved);
            String body = JSONUtils.serializeAsString(retrieved.asMap());
            return new StringRepresentation(body, MediaType.APPLICATION_JSON);
        } else {
            System.out.println(port + " handleLocalGet: id not found " + id);
            Map<String, Object> response = new HashMap<String, Object>();
            response.put("error", "not_found");
            response.put("reason", "missing");
            getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            String body = JSONUtils.serializeAsString(response);
            return new StringRepresentation(body, MediaType.APPLICATION_JSON);
        }
    }

    private Representation handleChangesGet(String path) {
        // TODO changes handling isn't required ??
        String body = "changes";
        return new StringRepresentation(body, MediaType.APPLICATION_JSON);
    }

    private String getDatabaseName(String path) {

        // look for expected replication url fragments
        // there's probably a nicer way of doing this :)

        int pos = path.indexOf("/_local/");
        if (pos > 0) {
            return path.substring(1, pos);
        }

        pos = path.indexOf("/_changes/");
        if (pos > 0) {
            return path.substring(1, pos);
        }

        pos = path.indexOf("/_revs_diff");
        if (pos > 0) {
            return path.substring(1, pos);
        }

        pos = path.indexOf("/_ensure_full_commit");
        if (pos > 0) {
            return path.substring(1, pos);
        }

        pos = path.indexOf("/_bulk_docs");
        if (pos > 0) {
            return path.substring(1, pos);
        }

        // if the url doesn't contain any of the above url fragments assume that the whole
        // path is the database name, and replace any leading and training forward slashes
        return path.replaceAll("^/", "").replaceAll("/$", "");
    }

    private Representation databaseNotFound() {
        getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND);
        return new StringRepresentation("404 - Database Not Found!", MediaType.TEXT_PLAIN);
    }

    private Representation lastSequence(String dbname) {
        // the update_seq value may not actually be required
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("instance_start_time", getInstanceStartTime(dbname).toString());
        response.put("update_seq", getLastSequence(dbname));
        String body = JSONUtils.serializeAsString(response);
        return new StringRepresentation(body, MediaType.APPLICATION_JSON);
    }

    private Representation httpSuccess() {
        // FIXME StringRepresentation is probably not the right choice as we aren't returning a body
        return new StringRepresentation("", MediaType.APPLICATION_JSON);
    }

    private boolean idAndRevExistLocally(String dbname, String docId, String rev) {
        Datastore ds;
		try {
			ds = manager.openDatastore(dbname);
		} catch (DatastoreNotCreatedException e) {
			throw new RuntimeException(e);
		}
		
        try {
			return ds.containsDocument(docId, rev);
		} finally {
			ds.close();
		}
    }

    private String buildRevsDiffResponse(String dbname) {
        Map<String, Object> requestJson = getRequestEntityAsMap();
        Map<String, Object> responseJson = new HashMap<String, Object>();

        Iterator requestIdIterator = requestJson.entrySet().iterator();
        while (requestIdIterator.hasNext()) {
            Map.Entry pair = (Map.Entry)requestIdIterator.next();

            String id = (String) pair.getKey();
            List<String> missingRevs = new ArrayList<String>();

            List<String> revs = (List<String>)pair.getValue();
            for (String rev : revs) {
                if (!idAndRevExistLocally(dbname, id, rev)) {
                    missingRevs.add(rev);
                }
            }
            if (missingRevs.size() > 0) {
                Map<String, Object> missingMap = new HashMap<String, Object>();
                missingMap.put("missing", missingRevs);
                responseJson.put(id, missingMap);
            }
        }
        String response = JSONUtils.serializeAsString(responseJson);
        //System.out.println("_revs_diff response: " + response);
        return response;
    }

    private Representation handleRevsDiff(String dbname) {
        String response = buildRevsDiffResponse(dbname);
        System.out.println(port + " handleRevsDiff:" + response);
        return new StringRepresentation(response, MediaType.APPLICATION_JSON);
    }

    private Map<String, Object> getRequestEntityAsMap() {
        Map<String, Object> requestJson;
        try {
            String requestText = getRequestEntity().getText();
            requestJson = JSONUtils.deserialize(requestText.getBytes());
        } catch (Exception e) {
            // TODO handle this properly
            throw new RuntimeException(e);
        }
        return requestJson;
    }

    private Representation handleEnsureFullCommitPost(String dbname) {
        // http://docs.couchdb.org/en/latest/replication/protocol.html#ensure-in-commit
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("instance_start_time", getInstanceStartTime(dbname));
        response.put("ok", true);
        String responseBody = JSONUtils.serializeAsString(response);
        //System.out.println(responseBody);
        getResponse().setStatus(Status.SUCCESS_CREATED);
        return new StringRepresentation(responseBody, MediaType.APPLICATION_JSON);
    }

    private List<String> appendRevisionSequence(List<String> revisionHistory, int startRevision) {
        List<String> newList = new ArrayList<String>();
        for (String item : revisionHistory) {
            newList.add(startRevision-- + "-" + item);
        }
        return newList;
    }

    private Representation handleBulkDocsPost(String dbname) {
        // http://docs.couchdb.org/en/latest/replication/protocol.html#upload-batch-of-changed-documents

        Datastore ds;
		try {
			ds = manager.openDatastore(dbname);
		} catch (DatastoreNotCreatedException e1) {
			throw new RuntimeException(e1);
		}

        Map<String, Object> bulkDocsRequest = getRequestEntityAsMap();
        System.out.println(port + " bulkDocsRequest: " + bulkDocsRequest);

        List<Map<String, Object>> docs = (List<Map<String, Object>>) bulkDocsRequest.get("docs");

        List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();

        for (Map<String, Object> doc : docs) {

            String docId = (String)doc.get("_id");
            String revId = (String)doc.get("_rev");

            Map<String, Object> revisionHistoryMap = (Map<String, Object>)doc.get("_revisions");
            int revStart = (Integer)revisionHistoryMap.get("start");

            List<String> revisionIds = (List<String>)revisionHistoryMap.get("ids");
            List<String> revisionHistoryList = appendRevisionSequence(revisionIds, revStart);

            // saving fails unless the revisionHistoryList is in reverse order
            Collections.reverse(revisionHistoryList);

            doc.remove("_revisions");
            doc.remove("_id");
            doc.remove("_rev");

            DocumentBody body = DocumentBodyFactory.create(doc);

            DocumentRevisionBuilder builder = new DocumentRevisionBuilder();
            builder.setDocId(docId);
            builder.setRevId(revId);
            builder.setBody(body);

            BasicDocumentRevision rev = builder.build();

            try {
				((DatastoreExtended)ds).forceInsert(rev, revisionHistoryList, null, null, false);
			} catch (DocumentException e) {
				throw new RuntimeException(e);
			}  
            
            Map<String, Object> responseItem = new TreeMap<String, Object>();
            responseItem.put("ok", true);
            responseItem.put("id", docId);
            responseItem.put("rev", revId);
            response.add(responseItem);
        }

        ds.close();

        Boolean newEdits = (Boolean)bulkDocsRequest.get("new_edits");
        
        String responseJSON = "";
        if (newEdits != null && !newEdits) {
        	responseJSON = "[]";
        } else {
        	responseJSON = ExtendedJSONUtils.serializeAsString(response);
        }
        System.out.println(port + " handleBulkDocsPost: " + responseJSON);
        getResponse().setStatus(Status.SUCCESS_CREATED);
        return new StringRepresentation(responseJSON, MediaType.APPLICATION_JSON);
    }

    private Representation handleLocalPut(String dbname) {

        Map<String, Object> request = getRequestEntityAsMap();

        System.out.println( JSONUtils.serializeAsString(request) );

        String docId = (String)request.get("_id");
        String revId = (String)request.get("_rev");

        request.remove("_id");
        request.remove("_rev");
        request.remove("_revision");
        request.remove("_revisions");
        
        DocumentBody body = DocumentBodyFactory.create(request);
        
        DocumentRevisionBuilder builder = new DocumentRevisionBuilder();
        builder.setDocId(docId);
        builder.setRevId(revId);
        builder.setDeleted(false);
        builder.setBody(body);
        BasicDocumentRevision doc = builder.build();

        Datastore ds = null;
		try {
			ds = manager.openDatastore(dbname);
		} catch (DatastoreNotCreatedException e1) {
			throw new RuntimeException(e1);
		}
		
        try {
            if (!ds.containsDocument(docId, revId)) {
               
                ((DatastoreExtended)ds).forceInsert(doc);
                
            } else {
                // FIXME the datastore shouldn't contain this revId
                System.out.println("XXXXXXXX " + docId + " " + revId);
            }
        } catch (Exception e) {
            // TODO handle properly (return 500?)
            throw new RuntimeException(e);
        } finally {
            ds.close();
        }

        Map<String, Object> response = new HashMap<String, Object>();
        response.put("id", docId);
        response.put("ok", true);
        response.put("rev", revId);

        String responseBody = JSONUtils.serializeAsString(response);

        System.out.println(responseBody);

        getResponse().setStatus(Status.SUCCESS_CREATED);
        return new StringRepresentation(responseBody, MediaType.APPLICATION_JSON);
    }

    private boolean dbNameExists(String name) {
        for (String db : manager.listAllDatastores()) {
            if (db.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private String getLastSequence(String dbname) {

        Datastore ds = null;
		try {
			ds = manager.openDatastore(dbname);
		} catch (DatastoreNotCreatedException e) {
			return null;
		}
        long seq = ds.getLastSequence();
        ds.close();

        return Long.toString(seq);
    }

    private String getInstanceStartTime(String dbname) {
        // FIXME
        return "1381218659871282";
    }
}