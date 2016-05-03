package com.cloudant.p2p.listener;

import com.cloudant.sync.datastore.Datastore;
import com.cloudant.sync.datastore.DatastoreManager;

import org.restlet.Component;
import org.restlet.Context;
import org.restlet.Server;
import org.restlet.data.Protocol;
import org.restlet.routing.Router;

import java.io.File;

/**
 * Created by snowch on 29/01/15.
 */
public class Application {

    private static String databaseDir = System.getProperty("DB_DIR", "/tmp/datastores/");

    public static void main(String[] args) throws Exception {
    	
    	final int port = 8182;

        createDevelopmentDatabase();

        // Set up a Restlet service
        final Router router = new Router();
        router.attachDefault(HttpListener.class);

        org.restlet.Application myApp = new org.restlet.Application() {
            @Override
            public org.restlet.Restlet createInboundRoot() {
            	Context ctx = getContext();
				ctx.getParameters().add("databaseDir", databaseDir);
				ctx.getParameters().add("port", Integer.toString(port));
				router.setContext(ctx);
                return router;
            };
        };
        Component component = new Component();
        component.getDefaultHost().attach("/", myApp);

        new Server(Protocol.HTTP, port, component).start();
    }


    private static void createDevelopmentDatabase() throws Exception {

        // some temporary code for development purposes :)
        File path = new File(databaseDir);
        DatastoreManager manager = new DatastoreManager(path.getAbsolutePath());

        // make sure we have a database for development
        Datastore ds = manager.openDatastore("mydb");

//        MutableDocumentRevision rev = new MutableDocumentRevision();
//        Map<String, Object> json = new HashMap<String, Object>();
//        json.put("description", "Buy milk");
//        json.put("completed", false);
//        json.put("type", "com.cloudant.sync.example.task");
//        rev.body = DocumentBodyFactory.create(json);
//        ds.createDocumentFromRevision(rev);
        ds.close();
    }
}
